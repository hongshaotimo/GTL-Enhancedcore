# GTL-Enhancedcore

GregTech Leisure Enhanced 整合包专用核心模组，扩展工业机器、仓室、巨构结构、自动化工具以及 GTCEu / AE2 / JEI / Jade 适配。单独安装无法运行。

- 模组 ID：`gtl_enhancedcore`
- 环境：Minecraft 1.20.1 / Forge 47.x / Java 21
- 当前版本：4.1.7（以 `gradle.properties` 为准）
- 许可：[LGPL-3.0](LICENSE)

## 主要功能

- 大型熔炉、基础矿石处理厂、四台 Plus 工厂、四台 IV 工厂和通用联合工厂。
- 超级样板总成隔离订单：跨配方队列、共享预算、库存/能量/产物持久化与取消退料。
- 恒星约束聚变堆、超构化学扭曲仪、铂系精炼矩阵、龙式场约束增殖核心、虚空约束采矿场、中子控制工厂、星核剥离装置、热压浸解联合体、超级粒子对撞机、星璇号空间站等后期巨构。
- 无线充能器（单方块机器）、末晶谐振器、ME 元件集成仓、量子数据访问仓、创造算力数据靶仓、电路编码仓和自适应维护仓。
- 私人维度创建器与私人维度传送器：每位玩家拥有独立维度，支持高度自定义。
- 中央样板库与云端样板总成：云端样板管理，可连接多个总成并自动适配机器模式。
- 样板生成工具、气象锚点、因果重构终端、领地置换终端及第三方结构/配方适配。

恒星约束聚变堆从无线电网扣 EU，并行与跨配方线程取无限上限，单批固定 20 tick，结构只接受输入/输出仓。星核剥离装置定位 UHV，碎片世界采集配方，固定 128 条跨配方线程。

## 依赖

需要 Forge 47.3.7+、Minecraft 1.20.1、GTCEu 1.4.4、GTLCore 1.2.3.2-fix4、GTLAdditions 3.3.1Custom、LDLib、GTMThings、AE2、ExtendedAE、FTB Chunks/Library/Teams 和 Architectury。JEI、Jade 为可选客户端依赖；准确版本以 `gradle/local-dependencies.json` 和整合包清单为准。

## 构建

需要 JDK 21，依赖 JAR 放在 `libs/`。Windows 执行：

```powershell
.\build.bat --no-pause --offline
```

产物位于 `build/libs/`；构建包含离线 `regressionTest`。

源码入口：`src/main/java`；资源和 Mixin：`src/main/resources`；离线回归：`src/test`；隔离审计：`src/audit`。

本项目采用 [GNU Lesser General Public License v3.0](LICENSE)。部分代码参考 GTL-supb、GTL-dishanhai 和 [GregTech CEu Modern](https://github.com/GregTechCEu/GregTech-Modern)。
