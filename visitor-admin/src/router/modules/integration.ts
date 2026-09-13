const Layout = () => import("@/layout/index.vue");

export default {
  path: "/integration-management",
  name: "IntegrationManagement",
  component: Layout,
  redirect: "/integrations",
  meta: {
    icon: "ri:radar-line",
    title: "集成管理",
    rank: 20
  },
  children: [
    {
      path: "/integrations",
      name: "Integrations",
      component: () => import("@/views/integrations/index.vue"),
      meta: {
        title: "运行状态",
        icon: "ri:pulse-line",
        permissions: ["integration:view"]
      }
    },
    {
      path: "/configs",
      name: "IntegrationConfigs",
      component: () => import("@/views/configs/index.vue"),
      meta: {
        title: "集成配置",
        icon: "ri:settings-4-line",
        permissions: ["config:view"]
      }
    }
  ]
} satisfies RouteConfigsTable;
