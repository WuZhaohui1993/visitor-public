const Layout = () => import("@/layout/index.vue");

export default {
  path: "/",
  name: "Home",
  component: Layout,
  redirect: "/dashboard",
  meta: {
    icon: "ri:dashboard-line",
    title: "工作台",
    rank: 0
  },
  children: [
    {
      path: "/dashboard",
      name: "Dashboard",
      component: () => import("@/views/dashboard/index.vue"),
      meta: {
        title: "工作台",
        icon: "ri:dashboard-line",
        fixedTag: true,
        permissions: ["dashboard:view"]
      }
    }
  ]
} satisfies RouteConfigsTable;
