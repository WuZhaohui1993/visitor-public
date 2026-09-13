# 访客管理系统

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) [![Vue 3](https://img.shields.io/badge/Vue-3.x-42b883.svg)](https://vuejs.org/) [![Java 17](https://img.shields.io/badge/Java-17%2B-ed8b00.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

面向访客登记、审批、结果查询和管理台账的前后端系统，集成层可按目标环境接入消息平台、门禁或人脸服务。

> 本项目只提供通用业务代码和接口抽象，不包含真实访客资料、身份证/人脸文件、厂商文档、生产地址、生产凭据或数据库快照。第三方设备接入需要在目标环境单独验收。

## 功能

- **访客登记**：H5 登记、申请查询、审批状态和结果展示。
- **管理台账**：访客记录、审批处理、用户与权限管理。
- **文件服务**：受控上传、对象存储抽象和短链访问。
- **集成适配**：消息通知、门禁/人脸服务接口和可替换的 Mock 适配器。
- **安全能力**：Token、敏感字段加密、权限校验和操作审计。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 访客端 | Vue 3、Vite |
| 管理端 | Vue 3、TypeScript、Vite、Element Plus |
| 后端 | Java 17、Spring Boot、JPA、JWT |
| 基础设施 | MySQL、对象存储、Maven |

## 快速开始

### 环境要求

Git、JDK 17+、Maven、Node.js、npm/pnpm 和 MySQL 8+。

### 配置与启动

后端使用 `visitor-server/src/main/resources/application.yml` 和 `hikvision.example.yml` 作为配置参考；数据库、Token、对象存储和第三方凭据通过环境变量注入。

```bash
cd visitor-server
./mvnw spring-boot:run

cd ../visitor-h5
npm install
npm run dev

cd ../visitor-admin
pnpm install
pnpm dev
```

本公开副本不提供数据库初始化脚本和厂商 SDK。没有外部服务时，请使用 Mock 适配器。

## 测试

```bash
cd visitor-server
./mvnw -DskipTests package
```

完整测试需要专用 H2/MySQL 测试配置；海康 Artemis SDK 未随仓库分发，启用真实适配器前需自行取得授权 SDK。

## 项目结构

```text
├── visitor-h5/       # 访客端
├── visitor-admin/    # 管理端
├── visitor-server/   # Spring Boot 后端
├── LICENSE
└── THIRD_PARTY_NOTICES.md
```

## 安全边界

访客身份信息、照片、Token、对象存储密钥和第三方 AppSecret 不得写入仓库。生产环境应配置 HTTPS、访问控制、文件隔离、审计日志和数据保留策略。

## 参与贡献

请保持接口、权限和前端行为同步；不要提交真实访客数据、厂商资料、生产配置、凭据或构建产物。提交 PR 时说明实际运行的测试和未覆盖范围。

## 许可证

本项目及其自有代码采用 MIT 许可证。厂商 SDK、RuoYi、vue-pure-admin 及其他依赖的许可证和再分发边界见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
