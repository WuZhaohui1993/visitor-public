package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminPermission;
import com.visitor.system.admin.domain.AdminRole;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.dto.AdminProfileResp;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.common.ForbiddenException;
import com.visitor.system.common.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAccessService {

    public AdminPrincipal principal(AdminUser user) {
        if (user == null || !user.isEnabled()) {
            throw new UnauthorizedException("管理员账号已停用");
        }
        Set<String> roles = user.getRoles().stream()
            .filter(AdminRole::isEnabled)
            .map(AdminRole::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> permissions = user.getRoles().stream()
            .filter(AdminRole::isEnabled)
            .flatMap(role -> role.getPermissions().stream())
            .map(AdminPermission::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AdminPrincipal(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            roles,
            permissions,
            user.isMustChangePassword()
        );
    }

    public AdminProfileResp profile(AdminUser user) {
        AdminPrincipal principal = principal(user);
        return AdminProfileResp.builder()
            .userId(principal.userId())
            .username(principal.username())
            .displayName(principal.displayName())
            .roles(principal.roles())
            .permissions(principal.permissions())
            .mustChangePassword(principal.mustChangePassword())
            .build();
    }

    public void require(AdminPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("当前管理员无权执行该操作");
        }
    }
}
