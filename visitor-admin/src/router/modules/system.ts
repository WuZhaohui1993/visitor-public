const Layout = () => import("@/layout/index.vue");

export default {
  path: "/system",
  name: "AdminSystem",
  component: Layout,
  redirect: "/system/users",
  meta: {
    icon: "ri:settings-3-line",
    title: "系统管理",
    rank: 30
  },
  children: [
    {
      path: "/system/users",
      name: "AdminUsers",
      component: () => import("@/views/system/users/index.vue"),
      meta: {
        title: "管理员",
        icon: "ri:user-settings-line",
        permissions: ["user:view"]
      }
    },
    {
      path: "/system/roles",
      name: "AdminRoles",
      component: () => import("@/views/system/roles/index.vue"),
      meta: {
        title: "角色权限",
        icon: "ri:shield-keyhole-line",
        permissions: ["role:view"]
      }
    },
    {
      path: "/audit",
      name: "AuditLogs",
      component: () => import("@/views/audit/index.vue"),
      meta: {
        title: "审计日志",
        icon: "ri:file-search-line",
        permissions: ["audit:view"]
      }
    }
  ]
} satisfies RouteConfigsTable;
