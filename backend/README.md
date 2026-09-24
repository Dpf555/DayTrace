# Spring Boot 业务后端

当前仅有 Maven 风格目录骨架，尚无 `pom.xml`、应用入口或业务代码。

## 目录职责

- `src/main/java/`：后续放置 Java 代码；基础包名在工程初始化时确定。
- `src/main/resources/`：应用资源与无敏感信息的公共配置。
- `src/main/resources/db/migration/`：数据库版本迁移脚本，不存放真实数据导出。
- `src/test/java/`：业务、权限与数据库集成测试。

计划按 `auth`、`journal`、`calendar`、`ai` 分包；各模块内采用 Controller → Service → Mapper。建议 Java 21、Spring Boot、Maven、Spring Security、MyBatis 和 MySQL，具体兼容版本在搭建时锁定。

Java 是业务数据的唯一写入入口，负责用户隔离、时间规则、事务与重复提交处理；不得信任请求中自行指定的用户身份。

参见 [系统架构](../docs/architecture.md) 与 [仓库首页](../README.md)。
