---
status: accepted
---

# 使用独立 Python AI 服务

DayTrace 的主要业务采用 Spring Boot，用户希望通过毕设学习 Python LangChain，因此选择由独立 Python 服务承载 AI 文字整理、润色和候选日程提取，而不是将 AI 全部集中于 Java 库。用户已明确接受跨服务通信、调试和部署的额外工作量；Spring Boot 继续负责账号、权限和全部业务数据保存，Python AI 服务不直接读写业务数据库，AI 结果经用户确认后才应用到业务数据。

具体 Python HTTP 框架、模型服务商和依赖版本在后续工程设计中共同确定。本决定记录架构方向，不代表授权生成完整业务代码。
