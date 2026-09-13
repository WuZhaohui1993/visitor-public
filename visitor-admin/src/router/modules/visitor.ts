const Layout = () => import("@/layout/index.vue");

export default {
  path: "/visitor-management",
  name: "VisitorManagement",
  component: Layout,
  redirect: "/records",
  meta: {
    icon: "ri:contacts-book-2-line",
    title: "访客管理",
    rank: 10
  },
  children: [
    {
      path: "/records",
      name: "VisitorRecords",
      component: () => import("@/views/records/index.vue"),
      meta: {
        title: "访客台账",
        icon: "ri:file-list-3-line",
        permissions: ["record:view"]
      }
    }
  ]
} satisfies RouteConfigsTable;
