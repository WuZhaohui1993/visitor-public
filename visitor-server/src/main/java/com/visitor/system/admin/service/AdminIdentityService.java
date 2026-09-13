package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminPermission;
import com.visitor.system.admin.domain.AdminRefreshSession;
import com.visitor.system.admin.domain.AdminRole;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.dto.AdminPasswordResetReq;
import com.visitor.system.admin.dto.AdminPermissionResp;
import com.visitor.system.admin.dto.AdminRoleResp;
import com.visitor.system.admin.dto.AdminRoleSaveReq;
import com.visitor.system.admin.dto.AdminUserCreateReq;
import com.visitor.system.admin.dto.AdminUserResp;
import com.visitor.system.admin.dto.AdminUserUpdateReq;
import com.visitor.system.admin.repository.AdminPermissionRepository;
import com.visitor.system.admin.repository.AdminRefreshSessionRepository;
import com.visitor.system.admin.repository.AdminRoleRepository;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.common.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminIdentityService {

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final AdminRefreshSessionRepository sessionRepository;
    private final AdminAuthService authService;
    private final PasswordEncoder passwordEncoder;

    public AdminIdentityService(AdminUserRepository userRepository,
                                AdminRoleRepository roleRepository,
                                AdminPermissionRepository permissionRepository,
                                AdminRefreshSessionRepository sessionRepository,
                                AdminAuthService authService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.sessionRepository = sessionRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResp> users() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream().map(this::toUser).toList();
    }

    @Transactional
    public AdminUserResp createUser(AdminUserCreateReq request) {
        String username = normalizeUsername(request.getUsername());
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new BusinessException("管理员账号已存在");
        }
        authService.validatePassword(request.getPassword());
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(resolveRoles(request.getRoleCodes()));
        user.setMustChangePassword(true);
        return toUser(userRepository.save(user));
    }

    @Transactional
    public AdminUserResp updateUser(Long userId, Long actorUserId, AdminUserUpdateReq request) {
        AdminUser user = requireUser(userId);
        if (userId.equals(actorUserId) && !request.isEnabled()) {
            throw new BusinessException("不能停用当前登录账号");
        }
        user.setDisplayName(request.getDisplayName().trim());
        user.setEnabled(request.isEnabled());
        user.setRoles(resolveRoles(request.getRoleCodes()));
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        revokeSessions(userId);
        return toUser(userRepository.save(user));
    }

    @Transactional
    public AdminUserResp resetPassword(Long userId, AdminPasswordResetReq request) {
        AdminUser user = requireUser(userId);
        authService.validatePasswordChange(user, request.getNewPassword());
        authService.rememberCurrentPassword(user);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        user.setPasswordChangedTime(LocalDateTime.now());
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        revokeSessions(userId);
        return toUser(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResp> roles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(this::toRole).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionResp> permissions() {
        return permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder")).stream().map(permission ->
            AdminPermissionResp.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .type(permission.getType())
                .routePath(permission.getRoutePath())
                .sortOrder(permission.getSortOrder())
                .build()
        ).toList();
    }

    @Transactional
    public AdminRoleResp createRole(AdminRoleSaveReq request) {
        String code = normalizeRoleCode(request.getCode());
        if (roleRepository.findByCode(code).isPresent()) {
            throw new BusinessException("角色编码已存在");
        }
        AdminRole role = new AdminRole();
        role.setCode(code);
        role.setBuiltIn(false);
        applyRole(role, request);
        return toRole(roleRepository.save(role));
    }

    @Transactional
    public AdminRoleResp updateRole(Long roleId, AdminRoleSaveReq request) {
        AdminRole role = roleRepository.findById(roleId).orElseThrow(() -> new BusinessException("角色不存在"));
        String code = normalizeRoleCode(request.getCode());
        if (role.isBuiltIn() && !role.getCode().equals(code)) {
            throw new BusinessException("内置角色编码不允许修改");
        }
        roleRepository.findByCode(code).filter(existing -> !existing.getId().equals(roleId)).ifPresent(existing -> {
            throw new BusinessException("角色编码已存在");
        });
        role.setCode(code);
        applyRole(role, request);
        AdminRole saved = roleRepository.save(role);
        userRepository.findAll().stream()
            .filter(user -> user.getRoles().stream().anyMatch(userRole -> userRole.getId().equals(roleId)))
            .forEach(user -> {
                user.setSecurityVersion(user.getSecurityVersion() + 1);
                userRepository.save(user);
                revokeSessions(user.getId());
            });
        return toRole(saved);
    }

    private void applyRole(AdminRole role, AdminRoleSaveReq request) {
        role.setName(request.getName().trim());
        role.setEnabled(request.isEnabled());
        role.setPermissions(resolvePermissions(request.getPermissionCodes()));
    }

    private LinkedHashSet<AdminRole> resolveRoles(Set<String> codes) {
        LinkedHashSet<AdminRole> roles = new LinkedHashSet<>();
        for (String code : codes) {
            AdminRole role = roleRepository.findByCode(normalizeRoleCode(code))
                .orElseThrow(() -> new BusinessException("角色不存在：" + code));
            if (!role.isEnabled()) {
                throw new BusinessException("角色已停用：" + role.getName());
            }
            roles.add(role);
        }
        return roles;
    }

    private LinkedHashSet<AdminPermission> resolvePermissions(Set<String> codes) {
        LinkedHashSet<AdminPermission> permissions = new LinkedHashSet<>();
        for (String code : codes) {
            permissions.add(permissionRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("权限不存在：" + code)));
        }
        return permissions;
    }

    private AdminUser requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException("管理员不存在"));
    }

    private void revokeSessions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (AdminRefreshSession session : sessionRepository.findByUserIdAndRevokedTimeIsNullAndExpiresTimeAfter(userId, now)) {
            session.setRevokedTime(now);
            sessionRepository.save(session);
        }
    }

    private String normalizeUsername(String username) {
        String normalized = StringUtils.lowerCase(StringUtils.trim(username));
        if (normalized == null || !normalized.matches("^[a-z0-9][a-z0-9._-]{2,39}$")) {
            throw new BusinessException("账号需为3至40位小写字母、数字、点、下划线或横线");
        }
        return normalized;
    }

    private String normalizeRoleCode(String code) {
        String normalized = StringUtils.upperCase(StringUtils.trim(code), Locale.ROOT);
        if (normalized == null || !normalized.matches("^[A-Z][A-Z0-9_]{2,39}$")) {
            throw new BusinessException("角色编码需为3至40位大写字母、数字或下划线");
        }
        return normalized;
    }

    private AdminUserResp toUser(AdminUser user) {
        return AdminUserResp.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .enabled(user.isEnabled())
            .mustChangePassword(user.isMustChangePassword())
            .roleCodes(user.getRoles().stream().map(AdminRole::getCode).collect(Collectors.toCollection(LinkedHashSet::new)))
            .lockedUntil(user.getLockedUntil())
            .lastLoginTime(user.getLastLoginTime())
            .createTime(user.getCreateTime())
            .build();
    }

    private AdminRoleResp toRole(AdminRole role) {
        return AdminRoleResp.builder()
            .id(role.getId())
            .code(role.getCode())
            .name(role.getName())
            .enabled(role.isEnabled())
            .builtIn(role.isBuiltIn())
            .permissionCodes(role.getPermissions().stream().map(AdminPermission::getCode).collect(Collectors.toCollection(LinkedHashSet::new)))
            .createTime(role.getCreateTime())
            .updateTime(role.getUpdateTime())
            .build();
    }
}
