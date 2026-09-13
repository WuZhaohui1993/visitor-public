package com.visitor.system.admin.service;

import com.visitor.system.admin.dto.AdminRouteResp;
import com.visitor.system.admin.security.AdminPermissions;
import com.visitor.system.admin.security.AdminPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminRouteService {

    public List<AdminRouteResp> routes(AdminPrincipal principal) {
        List<AdminRouteResp> routes = new ArrayList<>();
        add(routes, principal, AdminPermissions.DASHBOARD_VIEW,
            route("/dashboard", "Dashboard", "dashboard/index", "工作台", "ri:dashboard-line", 10));
        add(routes, principal, AdminPermissions.RECORD_VIEW,
            route("/records", "VisitorRecords", "records/index", "访客台账", "ri:contacts-book-2-line", 20));
        add(routes, principal, AdminPermissions.INTEGRATION_VIEW,
            route("/integrations", "Integrations", "integrations/index", "集成状态", "ri:radar-line", 30));
        add(routes, principal, AdminPermissions.CONFIG_VIEW,
            route("/configs", "IntegrationConfigs", "configs/index", "集成配置", "ri:settings-4-line", 40));

        List<AdminRouteResp> systemChildren = new ArrayList<>();
        add(systemChildren, principal, AdminPermissions.USER_VIEW,
            route("/system/users", "AdminUsers", "system/users/index", "管理员", "ri:user-settings-line", null));
        add(systemChildren, principal, AdminPermissions.ROLE_VIEW,
            route("/system/roles", "AdminRoles", "system/roles/index", "角色权限", "ri:shield-keyhole-line", null));
        if (!systemChildren.isEmpty()) {
            routes.add(AdminRouteResp.builder()
                .path("/system")
                .name("AdminSystem")
                .meta(meta("系统管理", "ri:settings-3-line", 50))
                .children(systemChildren)
                .build());
        }
        add(routes, principal, AdminPermissions.AUDIT_VIEW,
            route("/audit", "AuditLogs", "audit/index", "审计日志", "ri:file-list-3-line", 60));
        return routes;
    }

    private void add(List<AdminRouteResp> routes, AdminPrincipal principal, String permission, AdminRouteResp route) {
        if (principal.permissions().contains(permission)) {
            routes.add(route);
        }
    }

    private AdminRouteResp route(String path, String name, String component, String title, String icon, Integer rank) {
        return AdminRouteResp.builder()
            .path(path)
            .name(name)
            .component(component)
            .meta(meta(title, icon, rank))
            .children(List.of())
            .build();
    }

    private AdminRouteResp.Meta meta(String title, String icon, Integer rank) {
        return AdminRouteResp.Meta.builder().title(title).icon(icon).rank(rank).permissions(List.of()).build();
    }
}
