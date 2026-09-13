package com.visitor.system.admin.security;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AdminPermissions {

    private AdminPermissions() {
    }

    public static final String DASHBOARD_VIEW = "dashboard:view";
    public static final String RECORD_VIEW = "record:view";
    public static final String RECORD_EXPORT = "record:export";
    public static final String RECORD_OPERATE = "record:operate";
    public static final String INTEGRATION_VIEW = "integration:view";
    public static final String INTEGRATION_OPERATE = "integration:operate";
    public static final String CONFIG_VIEW = "config:view";
    public static final String CONFIG_EDIT = "config:edit";
    public static final String CONFIG_TEST = "config:test";
    public static final String CONFIG_SUBMIT = "config:submit";
    public static final String CONFIG_PUBLISH = "config:publish";
    public static final String USER_VIEW = "user:view";
    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_VIEW = "role:view";
    public static final String ROLE_MANAGE = "role:manage";
    public static final String AUDIT_VIEW = "audit:view";

    public static Map<String, PermissionSeed> seeds() {
        Map<String, PermissionSeed> seeds = new LinkedHashMap<>();
        seeds.put(DASHBOARD_VIEW, new PermissionSeed("查看工作台", "MENU", "/dashboard", 10));
        seeds.put(RECORD_VIEW, new PermissionSeed("查看访客台账", "MENU", "/records", 20));
        seeds.put(RECORD_EXPORT, new PermissionSeed("导出访客台账", "ACTION", null, 21));
        seeds.put(RECORD_OPERATE, new PermissionSeed("执行访客运维操作", "ACTION", null, 22));
        seeds.put(INTEGRATION_VIEW, new PermissionSeed("查看集成状态", "MENU", "/integrations", 30));
        seeds.put(INTEGRATION_OPERATE, new PermissionSeed("刷新集成资源", "ACTION", null, 31));
        seeds.put(CONFIG_VIEW, new PermissionSeed("查看集成配置", "MENU", "/configs", 40));
        seeds.put(CONFIG_EDIT, new PermissionSeed("编辑配置草稿", "ACTION", null, 41));
        seeds.put(CONFIG_TEST, new PermissionSeed("测试集成配置", "ACTION", null, 42));
        seeds.put(CONFIG_SUBMIT, new PermissionSeed("提交配置复核", "ACTION", null, 43));
        seeds.put(CONFIG_PUBLISH, new PermissionSeed("复核发布配置", "ACTION", null, 44));
        seeds.put(USER_VIEW, new PermissionSeed("查看管理员", "MENU", "/system/users", 50));
        seeds.put(USER_MANAGE, new PermissionSeed("管理管理员", "ACTION", null, 51));
        seeds.put(ROLE_VIEW, new PermissionSeed("查看角色权限", "MENU", "/system/roles", 60));
        seeds.put(ROLE_MANAGE, new PermissionSeed("管理角色权限", "ACTION", null, 61));
        seeds.put(AUDIT_VIEW, new PermissionSeed("查看审计日志", "MENU", "/audit", 70));
        return seeds;
    }

    public record PermissionSeed(String name, String type, String routePath, int sortOrder) {
    }
}
