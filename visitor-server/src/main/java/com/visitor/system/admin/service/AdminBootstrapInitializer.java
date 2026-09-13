package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminPermission;
import com.visitor.system.admin.domain.AdminRole;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.repository.AdminPermissionRepository;
import com.visitor.system.admin.repository.AdminRoleRepository;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.admin.security.AdminPermissions;
import com.visitor.system.config.VisitorProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    private final VisitorProperties properties;
    private final AdminPermissionRepository permissionRepository;
    private final AdminRoleRepository roleRepository;
    private final AdminUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapInitializer(VisitorProperties properties,
                                     AdminPermissionRepository permissionRepository,
                                     AdminRoleRepository roleRepository,
                                     AdminUserRepository userRepository,
                                     PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getAdmin().isEnabled()) {
            return;
        }
        Map<String, AdminPermissions.PermissionSeed> seeds = AdminPermissions.seeds();
        Set<AdminPermission> allPermissions = new LinkedHashSet<>();
        seeds.forEach((code, seed) -> {
            AdminPermission permission = permissionRepository.findByCode(code).orElseGet(AdminPermission::new);
            permission.setCode(code);
            permission.setName(seed.name());
            permission.setType(seed.type());
            permission.setRoutePath(seed.routePath());
            permission.setSortOrder(seed.sortOrder());
            allPermissions.add(permissionRepository.save(permission));
        });

        AdminRole superAdmin = upsertRole("SUPER_ADMIN", "系统管理员", allPermissions);
        upsertRole("OPERATOR", "运营管理员", filterPermissions(allPermissions, Set.of(
            AdminPermissions.DASHBOARD_VIEW,
            AdminPermissions.RECORD_VIEW,
            AdminPermissions.RECORD_EXPORT,
            AdminPermissions.RECORD_OPERATE,
            AdminPermissions.INTEGRATION_VIEW,
            AdminPermissions.INTEGRATION_OPERATE,
            AdminPermissions.CONFIG_VIEW,
            AdminPermissions.CONFIG_EDIT,
            AdminPermissions.CONFIG_TEST,
            AdminPermissions.CONFIG_SUBMIT
        )));
        upsertRole("AUDITOR", "只读审计员", filterPermissions(allPermissions, Set.of(
            AdminPermissions.DASHBOARD_VIEW,
            AdminPermissions.RECORD_VIEW,
            AdminPermissions.RECORD_EXPORT,
            AdminPermissions.INTEGRATION_VIEW,
            AdminPermissions.CONFIG_VIEW,
            AdminPermissions.AUDIT_VIEW
        )));

        String username = StringUtils.trimToNull(properties.getAdmin().getBootstrapUsername());
        String password = StringUtils.trimToNull(properties.getAdmin().getBootstrapPassword());
        if (userRepository.count() == 0 && username != null && password != null) {
            AdminUser user = new AdminUser();
            user.setUsername(username.toLowerCase());
            user.setDisplayName("系统管理员");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRoles(new LinkedHashSet<>(Set.of(superAdmin)));
            userRepository.save(user);
            log.warn("已创建后台初始管理员 {}，首次登录必须修改密码", username);
        } else if (userRepository.count() == 0) {
            log.warn("后台管理已启用但尚无管理员，请通过 VISITOR_ADMIN_BOOTSTRAP_USERNAME/PASSWORD 完成首次启动");
        }
    }

    private AdminRole upsertRole(String code, String name, Set<AdminPermission> permissions) {
        AdminRole role = roleRepository.findByCode(code).orElseGet(AdminRole::new);
        role.setCode(code);
        role.setName(name);
        role.setEnabled(true);
        role.setBuiltIn(true);
        role.setPermissions(new LinkedHashSet<>(permissions));
        return roleRepository.save(role);
    }

    private Set<AdminPermission> filterPermissions(Set<AdminPermission> permissions, Set<String> codes) {
        Set<AdminPermission> filtered = new LinkedHashSet<>();
        permissions.stream().filter(permission -> codes.contains(permission.getCode())).forEach(filtered::add);
        return filtered;
    }
}
