# Python AI 服务

当前仅有目录骨架，尚无 Python 应用入口、依赖清单或模型调用代码。

## 目录职责

- `app/api/`：内部 HTTP 路由。
- `app/schemas/`：输入输出数据结构。
- `app/services/`：要点整理、润色与候选日程提取。
- `app/prompts/`：任务提示词及其版本。
- `app/providers/`：模型适配。
- `tests/`：接口契约、异常处理与样例测试。

已确定使用 Python LangChain，建议搭配 FastAPI 与 Pydantic。具体版本、模型服务商和预算在接入前确定。

服务仅接收 Java 提供的本次必要输入，不直接访问业务数据库。先用固定样例打通接口，再接真实模型；不默认记录私人正文或自动重试付费调用。

参见 [系统架构](../docs/architecture.md) 与 [仓库首页](../README.md)。
