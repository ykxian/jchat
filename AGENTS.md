# AGENTS

本仓库当前以文档为主，代码尚未完整落地。  
后续任何编码代理在开始实现前，优先阅读：

1. [docs/CODEX-IMPLEMENTATION-ROADMAP.md](/home/ykx/jchat/docs/CODEX-IMPLEMENTATION-ROADMAP.md)
2. [README.md](/home/ykx/jchat/README.md)
3. [docs/ARCHITECTURE.md](/home/ykx/jchat/docs/ARCHITECTURE.md)
4. [docs/API.md](/home/ykx/jchat/docs/API.md)
5. [docs/DATA-MODEL.md](/home/ykx/jchat/docs/DATA-MODEL.md)
6. [docs/IMPLEMENTATION-STATUS.md](/home/ykx/jchat/docs/IMPLEMENTATION-STATUS.md)

## Primary Execution Guide

实现顺序、范围收敛、阶段拆分、每阶段的完成标准，以
[docs/CODEX-IMPLEMENTATION-ROADMAP.md](/home/ykx/jchat/docs/CODEX-IMPLEMENTATION-ROADMAP.md)
为主。

如果其他文档与该路线图存在轻微冲突，先按路线图中的收敛决策执行，再在实现过程中同步修正文档。

## Working Rules

- 不要一次生成整个项目，按路线图中的 phase 逐步推进。
- 每次只做一个可验证的子目标。
- 先完成主链路，再做高级能力。
- 每一步结束时，提供可运行或可构建的验证方式。
- 非当前 phase 的能力不要顺手扩展。

## Current Recommendation

从 `Phase 0` 开始：

- 初始化 monorepo 骨架
- 建立 frontend / backend 基础脚手架
- 配置 docker compose、环境变量模板、Makefile
- 先让项目跑起来，再进入 auth 和 chat
