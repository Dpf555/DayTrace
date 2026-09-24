# Spring Boot 业务后端

已导入用户通过 Spring Initializr 生成的工程：Java 21、Spring Boot 4.1.1、Spring Web MVC；已完成 hello 学习接口和 validation 参数校验。正式业务与数据库尚未接入。

## 目录职责

- `src/main/java/`：Java 代码，基础包名为 `com.dpf555.daytrace`。
- `src/main/resources/`：应用资源与无敏感信息的公共配置。
- `src/main/resources/db/migration/`：数据库版本迁移脚本，不存放真实数据导出。
- `src/test/java/`：业务、权限与数据库集成测试。

计划按 `auth`、`journal`、`calendar`、`ai` 分包；各模块内采用 Controller → Service → Mapper。Spring Security、MyBatis 和 MySQL 后续按模块接入，接入前核对兼容版本。

## 在 IDEA 中打开

选择 File → Open，打开本目录中的 `pom.xml` 并作为项目加载，等待 Maven 同步。Project SDK 使用 JDK 21。

运行 `src/main/java/com/dpf555/daytrace/DaytraceApplication.java` 的 main 方法。当前没有首页，访问根路径返回 404 不代表启动失败。

若环境变量 `SERVER_PORT` 覆盖了配置文件，可在本项目 Run Configuration 的 Program arguments 中填写 `--server.port=8081`，不必修改其他项目的环境设置。

## 当前学习接口

| 接口 | 输入 | 行为 |
| --- | --- | --- |
| `GET /api/v1/hello` | Query：name 可省略，默认 DayTrace；message 必填 | 返回 name 与客户端传来的 message |
| `POST /api/v1/hello` | JSON：name | 返回 name 与后端生成的 `Hello,<name>!` |

POST 使用 `@Valid`、`@NotBlank`、`@Size(max=30)`；缺失、null、空白或过长姓名返回默认 HTTP 400。GET 没有应用这些 DTO 校验。学习接口不属于正式 API 契约，尚未采用统一响应和 422 校验错误约定。

## 2026-09-24 验证记录

- Maven Wrapper `-B -ntp package`：BUILD SUCCESS，1 个上下文加载测试通过。
- 使用本轮构建产物在随机本机端口启动，12 个 HTTP 检查通过：GET 显式参数、默认姓名、缺少 message；POST 正常与中文姓名、缺失、null、空串、空白、30／31 字符边界、无效 JSON。
- 成功响应同时核对 name 与 message；仅结束验证所创建的进程，没有停止 IDEA 中的服务。
- 这些证据只覆盖学习接口，不代表正式业务、账号隔离或模型效果已验证。

## 命令行

在本目录的 PowerShell 中运行，需可用的 JDK 21：

```powershell
# 运行生成的上下文加载测试
.\mvnw.cmd test

# 启动后端，默认端口 8080
.\mvnw.cmd spring-boot:run
```

首次执行需要联网下载 Maven 和依赖。此处测试仅覆盖应用上下文加载，不代表业务功能已验证。

Java 是业务数据的唯一写入入口，负责用户隔离、时间规则、事务与重复提交处理；不得信任请求中自行指定的用户身份。

参见 [系统架构](../docs/architecture.md) 与 [仓库首页](../README.md)。
