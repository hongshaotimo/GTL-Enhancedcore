# GTL-Enhancedcore

Minecraft 1.20.1 / Forge 整合包专属核心，提供机器与仓室、隔离配方订单、结构适配、样板工具、无线能源和兼容修复。主工程使用 Java 21。

**换窗口或新 AI 接手，从 [AGENTS.md](AGENTS.md) 开始。** 当前版本、部署与待办只看 [启动上下文](启动上下文.md)，不需要翻历史日志。

## 文档导航

| 需要了解 | 入口 |
|---|---|
| 怎么执行任务、如何保护/验证/清理 | [执行规则](执行规则.md) |
| 当前项目全貌与接手位置 | [启动上下文](启动上下文.md) |
| 现行 API 和实现约定 | [API 标准](API标准.md) |
| 机器功能和已确认规格 | [功能与需求](功能与需求.md) |
| 尚未解决/验收的事项 | [已知问题](已知问题.md) |
| 服务端、客户端补丁和源码发布 | [打包](打包.md) |
| 近期工作、版本摘要 | [工作日志](工作日志.md) · [变更历史](变更历史.md) |
| 旧材料追溯 | [归档索引](docs/archive/README.md) |

## 构建

```powershell
.\build.bat --no-pause --offline
# 仅在需要部署时显式添加 --deploy；从实例刷新依赖添加 --sync-dependencies。
```

依赖文件名由 `gradle/local-dependencies.json` 定义，本地放在 `libs/`；版本唯一来源为 `gradle.properties`。
运行验收使用隔离的实际 Forge 环境；当前成品 JAR 编译方式下，不直接用 Gradle `runClient/runServer`。
完整成品审计需要运行期可选依赖，命令见工具索引；不要把仅命名空间检查当全量审计。
结构工具见 [SchemTool](SchemTool/README.md)，维护工具见 [工具索引](tools/README.md)。
