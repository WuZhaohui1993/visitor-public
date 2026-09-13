# 访客管理中心

独立访客后台前端，基于 `vue-pure-admin v7.0.0`（上游 tag commit：`b8177a202dceb1412d3bf56f3d15483dc1606c9c`）定制。

## 能力范围

- 管理员登录、首次改密、动态角色权限
- 工作台、访客台账、明文详情与 XLSX 导出
- 海康身份/门禁重试及权限回收
- 集成状态、访问资源和版本化配置发布
- 管理员、角色权限和审计日志

管理端只调用 `/api/admin/**`。访客记录本身只读，除明确的海康运维操作外，不提供修改登记或审批结果的入口。

## 本地运行

环境要求：Node.js `^20.19.0 || >=22.13.0`、pnpm `>=9`。

```bash
pnpm install
pnpm dev
```

默认地址：`http://127.0.0.1:51767`，开发代理指向 `http://127.0.0.1:9988`。

## 验证与构建

```bash
pnpm typecheck
pnpm build
```

构建产物位于 `dist/`。生产部署参数见 [`../docs/admin-platform.md`](../docs/admin-platform.md)。
