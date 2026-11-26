# Fieldward MVP 重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成从简历、方向探索、实时诊断，到学习、模拟面试、岗位匹配及投递记录的可恢复求职闭环。

**Architecture:** Vue 3、Spring Boot 和 PostgreSQL 承担业务、文字对话、长期记忆和积分；新增 Python 服务承担实时电话与视频对话编排。Java 是用户状态、能力证据和计费权威，Python 通过受认证接口提交会话要点与用量；现有数据增量迁移。

**Tech Stack:** Java 21、Spring Boot、MyBatis、Flyway、PostgreSQL、Vue 3、TypeScript、Vite、Python、小米 MiMo；Python 框架及实时音视频供应商根据任务 5 验证后选择。

**Spec:** `openspec/changes/redesign-mvp/proposal.md`、`design.md`、`tasks.md` 和 `specs/*/spec.md`。遇到冲突先以 change 中规格为准，并在实施前解决冲突。

## Global Constraints

- 首期网页适配移动端，邮箱密码登录，管理员一种角色。
- MVP 支持技术类实习与校招，全岗位市场对用户开放；站外来源网站完成投递。
- 用户先提交简历，可只填写姓名和学校；允许无方向探索及通用学习。
- 首次诊断由 Mentor 选择关键模块进行电话或视频对话，结束后统一反馈；诊断完成前不显示岗位匹配度。
- 用户自主选择学习主题、电话或视频；无站外提醒、独立 App、充值或企业端投递。
- 文字对话共享内部长期记忆；忘记范围、方向切换必须明确确认。
- 音视频结束仅保留报告、面经和必要回答要点，清除录音、录像及完整逐字稿。
- 一次性积分包含所有 AI 功能并为首次试用各阶段预留；约 20 元成本目标必须实测，不能假定供应商价格或额度。
- 毕业届别、地点、到岗时间、实习时长仅提示，不纳入匹配；待验证能力不计已掌握；仪态不计匹配。
- 历史简历、原件、旁聊和能力记录保留；迁移不将无法确认的旧自评当作已验证能力。
- 默认在项目根目录执行；不要输出、提交或复制 `.env` 中的凭据。
- 每个任务完成后只提交该任务涉及的文件。现有 change 文档和计划是未跟踪内容，首次提交时必须明确包含，经 `git diff --cached --check` 核对。

## 文件与责任边界

| 路径 | 责任 |
| --- | --- |
| `api/src/main/resources/db/migration/V12__career_capabilities.sql` 起 | 每个任务按实际最新版本递增创建 Flyway 迁移，负责路线、证据和业务实体 |
| `api/src/main/java/com/fieldward/mentor/Career*.java` | 职业方向、诊断入口、原有报告兼容 |
| `api/src/main/java/com/fieldward/mentor/Mentor*.java` | 对话、记忆、引导、模型调用和检索 |
| `api/src/main/java/com/fieldward/mentor/Skill*.java` | 兼容历史技能核验，新增能力验证入口 |
| `api/src/main/java/com/fieldward/resume/` | 原件、可编辑简历、版本及并发保护 |
| `api/src/main/java/com/fieldward/account/` | 学生登录及管理员权限 |
| `api/src/main/java/com/fieldward/{realtime,jobs,credits}/` | 分别承载实时会话授权与结算、岗位来源与匹配、积分账本 |
| `realtime-service/` | Python 实时音视频处理、供应商适配、会话事件和短期缓存清理 |
| `web/src/views/`、`web/src/components/`、`web/src/router.ts` | 各功能视图、可复用控件与路由 |
| `api/src/test/java/com/fieldward/` | JUnit 集成与领域测试，使用独立测试数据库 |
| `web/src/**/*.test.ts` | 涉及状态流转的前端组件测试 |
| `openspec/changes/redesign-mvp/tasks.md` | 与实现任务逐项同步的 OpenSpec 跟踪清单 |

## 执行约定

- 基线检查运行 `mvn -f api/pom.xml test` 和 `npm --prefix web run build`。先确认独立测试数据库连接方式，测试禁止清空共享数据库。
- 每个任务先写列出的失败场景并运行，确认失败原因对应缺失功能；再实现、重新运行并检查迁移/权限。基线缺少测试依赖时，在首个需要测试的任务中添加 Spring Boot 测试依赖和独立数据库配置。
- 每个 API 读取当前认证账号，不信任请求体中的账号身份。所有写操作处理重复请求、重试和并发。
- 每个任务末尾执行 `git diff --check`、相关测试、前端构建（若改动前端），更新对应 OpenSpec 复选框，再按文件范围提交。
- 真实第三方集成使用合成数据；不把真实简历、密钥或个人数据放进测试日志。

## 阶段 A — 可追溯能力与账户基础

### Task 1: 职业方向、路线版本和能力证据

**Files:** Create `api/src/main/resources/db/migration/V12__career_capabilities.sql`、`api/src/main/java/com/fieldward/mentor/CapabilityMapper.java`、`CapabilityService.java`、`api/src/test/java/com/fieldward/mentor/CapabilityServiceTest.java`；Modify `CareerCatalog.java`、`CareerController.java`。

**Interfaces:** `CapabilityService.recordEvidence(long accountId, String capabilityId, EvidenceInput input): Evidence`；`currentRoute(long accountId, String directionId): RouteSnapshot`；证据包含 `sourceType`、`sourceId`、`outcome`、`createdAt`。路线以稳定能力 ID 关联版本。

- [ ] 1.1 先测试两个方向共享同一能力 ID 时证据可复用，另一个账号无法读取；验证路线升级删除条目后旧证据仍在。
- [ ] 1.2 添加方向、能力、路线版本和证据表，创建带账户条件的 Mapper 与事务服务；已有目录作为首次初始化来源，后续由管理员维护。
- [ ] 1.3 运行 `mvn -f api/pom.xml -Dtest=CapabilityServiceTest test`，验证迁移可重复启动且证据来源不可被覆盖。

### Task 2: 旧报告和会话兼容

**Files:** Create `api/src/main/resources/db/migration/V13__legacy_capability_mapping.sql`、`api/src/test/java/com/fieldward/mentor/LegacyCareerMigrationTest.java`；Modify `CareerPlans.java`、`SkillSessions.java`、`CareerMapper.java`。

**Interfaces:** `LegacyCareerMapper.importAssessment(long accountId, long assessmentId): MigrationResult`，结果包含 `imported`、`unmapped`；历史记录保留原 ID 和来源。

- [ ] 2.1 建立 V10/V11 测试夹具，包含旧自评、逐题评分、上传文件和旁聊；测试未知映射保持待验证且旧会话仍可读取。
- [ ] 2.2 增量映射可识别记录并记录迁移版本；重复执行不重复写证据，原报告和原文件继续可访问。
- [ ] 2.3 运行 `mvn -f api/pom.xml -Dtest=LegacyCareerMigrationTest test`，验证原账号与另一账号隔离。

### Task 3: 积分账本与阶段预留

**Files:** Create `api/src/main/java/com/fieldward/credits/CreditService.java`、`CreditMapper.java`、`CreditController.java`、对应 Flyway 迁移及 `CreditServiceTest.java`；Modify `AccountMapper.java`、`AdminController.java`、`web/src/views/AdminUsersView.vue`。

**Interfaces:** `reserve(accountId, requestId, stage, maxPoints): Reservation`；`settle(requestId, actualPoints): LedgerEntry`；`release(requestId): void`。账本通过 requestId 实现幂等，管理员调整保留操作者与原因。

- [ ] 3.1 编写并发测试，两个请求不能超额占用；同一 requestId 重试只能结算一次；释放只返还未使用部分；普通自由对话不能消耗保留给首次流程的积分。
- [ ] 3.2 事务实现账户、阶段预留及不可变账本；注册创建一次性账户；管理员按账号调整并写审计流水。
- [ ] 3.3 运行 `mvn -f api/pom.xml -Dtest=CreditServiceTest test`；验证空账户不能发起新 AI 调用，但可访问岗位和已有内容。积分数值与供应商定价由 Task 5 测算后配置。

## 阶段 B — Mentor、诊断与实时通话

### Task 4: 探索入口与共享记忆

**Files:** Modify `CareerPlans.java`、`CareerWrites.java`、`MentorTurns.java`、`MentorMemory.java`、`web/src/views/CareerDirectionView.vue`、`WorkspaceView.vue`、`web/src/router.ts`；Create `api/src/test/java/com/fieldward/mentor/ExplorationFlowTest.java`。

**Interfaces:** `CareerPlans.beginExploration(long accountId): ExplorationState`；`MentorMemory.retrieve(StoredTurn turn, Set<Long> recentIds)` 保留现有签名，扩充来源与当前目标上下文。

- [ ] 4.1 测试只填姓名和学校后可进入无方向探索，Mentor 主动发问；主对话与旁聊均能获取相关记忆且另一用户不能读取。
- [ ] 4.2 调整 onboarding gate，保留已有用户访问；增加通用学习入口；从用户导航和路由移除记忆查看/编辑页面，同时保留内部记忆能力。
- [ ] 4.3 运行 `mvn -f api/pom.xml -Dtest=ExplorationFlowTest test` 和 `npm --prefix web run build`，人工检查移动端导航。

### Task 5: 实时供应商与试用成本验证

**Files:** Create `docs/superpowers/plans/fieldward-realtime-cost-evaluation.md`；后续实现入口 `realtime-service/` 及 `api/src/main/java/com/fieldward/realtime/RealtimeController.java`。

**Interfaces:** Python 适配器 `open(mode, context) -> ProviderSession`，`mode = AUDIO | VIDEO`；适配器须支持音频输入输出、可用视频帧、会话关闭和可核算用量。Java 负责授权、记忆和积分的权威状态。

- [ ] 5.1 查阅候选供应商官方接口与当前按量价格，验证 Web 浏览器、中文语音、视觉输入、服务端凭据保护、结束清理和使用条件，记录来源及日期。
- [ ] 5.2 使用合成简历测量一次诊断、若干学习轮、一项知识验证、一次模拟面试和岗位分析的费用；记录输入/输出量、时长、并发和失败重试成本。
- [ ] 5.3 用测得成本制定阶段预留和每项积分换算，验证至少一次完整试用预计不超过约 20 元。若不满足，记录具体差距并暂停依赖该结论的上线任务，向用户报告；不虚构价格或保证。
- [ ] 5.4 对测试账号验证供应商是否持久化音视频或逐字稿；若保留方式与规格不符，不能选用该配置。

### Task 6: 实时会话生命周期与隐私

**Files:** Create `realtime-service/` 中的 Python 供应商适配与实时会话处理、`api/src/main/java/com/fieldward/realtime/RealtimeSessionService.java`、`RealtimeController.java`、相应 Flyway 迁移、`RealtimeSessionServiceTest.java`、`web/src/components/RealtimeSession.vue`；Modify `web/src/router.ts`。

**Interfaces:** `start(accountId, purpose, mode, requestId): SessionView`；`checkpoint(accountId, sessionId, revision, summary): SessionView`；`finish(accountId, sessionId, requestId): SessionView`；状态 `ACTIVE | PAUSED | COMPLETED`。

- [ ] 6.1 用伪供应商测试电话不请求摄像头、视频请求摄像头、断线后恢复摘要和修订号、重复结束只产生一份结果。
- [ ] 6.2 建立 Java 到 Python 的受认证会话授权及 Python 到 Java 的幂等事件接口，按会话和账号校验上下文读取；测试跨账号拒绝、事件乱序、重复回调与服务重启。
- [ ] 6.3 将账本预留与分段结算接入会话；低余额时提醒并保存进度；结束、异常与超时清除临时媒体和完整逐字稿，包括中间缓存。
- [ ] 6.4 执行 `mvn -f api/pom.xml -Dtest=RealtimeSessionServiceTest test`，并在桌面及手机浏览器验证授权、拒绝授权、断线和恢复。

### Task 7: 首次诊断与方向切换

**Files:** Create `api/src/main/java/com/fieldward/mentor/DiagnosisService.java`、`DiagnosisController.java`、`DirectionChangeService.java`、`DiagnosisServiceTest.java`、`web/src/views/DiagnosisView.vue`；Modify `CareerFit.java`、`CareerDirectionView.vue`。

**Interfaces:** `begin(accountId, directionId, resumeVersion, mode): DiagnosisView`；`complete(accountId, sessionId, requestId): DiagnosisReport`；`confirmDirectionChange(accountId, proposalId): DirectionView`。

- [ ] 7.1 测试 AI 根据方向和简历挑重点、自适应追问、结束前不逐题反馈；Spring Boot 项目不触发 Java 全部技术点逐题测验。
- [ ] 7.2 保存完成的报告、待验证项、能力证据和后续路线任务；重复完成不得重复建立证据；电话报告仪态为未评估，视频建议不写能力分数。
- [ ] 7.3 测试提出换方向后说明介绍、路线差异与建议；只有明确肯定才切换，共同能力证据沿用。
- [ ] 7.4 运行 `mvn -f api/pom.xml -Dtest=DiagnosisServiceTest test` 和前端构建，验证首次诊断前岗位无匹配度。

### Task 8: 记忆遗忘确认与清理

**Files:** Create `api/src/main/java/com/fieldward/mentor/ForgetRequestService.java`、`ForgetRequestServiceTest.java`；Modify `MentorMemoryMapper.java`、`MentorSearch.java`、`MentorTurns.java`、`MentorLibraryController.java`。

**Interfaces:** `propose(accountId, description): ForgetProposal`；`confirm(accountId, proposalId, scope): ForgetResult`；scope 明确区分记忆及列出的对话、文件、简历版本。

- [ ] 8.1 测试未确认不执行、确认后仅清理选定对象、跨账号无权删除、被删除事实无法通过全文检索或摘要重新取回。
- [ ] 8.2 在事务中记录用户确认，清理引用与派生摘要，必要的原始资料按用户确认范围处理；避免其他资料被级联删除。
- [ ] 8.3 运行 `mvn -f api/pom.xml -Dtest=ForgetRequestServiceTest test` 并检查数据库全文索引查询结果。

### Task 9: 学习、验证和面经

**Files:** Create `api/src/main/java/com/fieldward/mentor/LearningService.java`、`InterviewService.java`、`InterviewController.java`、`LearningServiceTest.java`、`web/src/views/LearningRouteView.vue`、`InterviewReportView.vue`；Modify `MentorTurns.java`。

**Interfaces:** `openTopic(accountId, capabilityId): SideChat`；`verify(accountId, capabilityId, answer): Evidence`；`interviewReport(accountId, interviewId): ReportView`。

- [ ] 9.1 测试自由选课、讲练后新题验证、用户质疑结论后的核实、已有通过证据保留并建议复习。
- [ ] 9.2 实现方向/岗位模拟面试结束后统一生成面经和反馈；从某题打开对应知识点旁聊时复用历史线程。
- [ ] 9.3 运行 `mvn -f api/pom.xml -Dtest=LearningServiceTest test`，验证手机浏览器可完成文字学习并切换到实时模式。

## 阶段 C — 简历与岗位闭环

### Task 10: 原件转可编辑简历和自动版本更新

**Files:** Modify `api/src/main/java/com/fieldward/resume/ResumeSubmissions.java`、`ResumeVersionMapper.java`、`web/src/views/ResumeFinalView.vue`；Create `api/src/main/java/com/fieldward/resume/ResumeUpdateService.java`、`ResumeUpdateServiceTest.java`。

**Interfaces:** `convertUpload(accountId, version): ConversionResult`；`applyEvidence(accountId, baseVersion, evidenceId, requestId): ResumeVersion`。

- [ ] 10.1 测试 PDF/DOCX 原件保留、解析失败提示补充、根据证据创建新版本并在主对话通知。
- [ ] 10.2 测试学习成果不会新增虚构项目，用户编辑与 Mentor 同时写入时使用版本检查并重新基于最新版本处理。
- [ ] 10.3 测试历史版本查看和恢复；运行 `mvn -f api/pom.xml -Dtest=ResumeUpdateServiceTest test` 和前端构建。

### Task 11: 岗位来源和管理

**Files:** Create `api/src/main/java/com/fieldward/jobs/JobSource.java`、`JobSyncService.java`、`JobMapper.java`、`JobAdminController.java`、`JobSyncServiceTest.java`、Flyway 迁移、`web/src/views/AdminJobsView.vue`。

**Interfaces:** `JobSource.fetchChanges(cursor): SourceBatch`；`JobSyncService.sync(sourceId): SyncResult`。岗位以来源与来源岗位 ID 去重，版本保存原始要求、链接及核验时间。

- [ ] 11.1 用伪来源测试增量同步、更新重复岗位、自动展示、同步失败不错误下架、管理员手动下架。
- [ ] 11.2 核实企业官网或授权来源可用性与条件，接入至少一个经核实来源；BOSS 接口无可验证授权时不假定可用。
- [ ] 11.3 运行 `mvn -f api/pom.xml -Dtest=JobSyncServiceTest test`，验证管理员页面只能由管理员访问。

### Task 12: 全岗位匹配与自动刷新

**Files:** Create `api/src/main/java/com/fieldward/jobs/JobRequirementExtractor.java`、`JobMatchService.java`、`JobMatchController.java`、`JobMatchServiceTest.java`、`web/src/views/JobMarketView.vue`、`JobDetailView.vue`。

**Interfaces:** `extract(jobVersion): RequirementSnapshot`；`match(accountId, jobId): MatchView`；`enqueueRefresh(accountId, evidenceId): void`。结果包含百分比、证据、待验证项、刷新状态及版本。

- [ ] 12.1 测试诊断前返回无分数，诊断后全部岗位可计算；待验证项不计已掌握；地点、届别、到岗和实习时长变更不改变分数。
- [ ] 12.2 按岗位版本复用 AI 抽取结果，按证据版本增量重算；记录计算中和更新时间；确保后台重试不会重复扣用户积分。
- [ ] 12.3 测试高匹配排序、跨方向浏览、证据变化与岗位变更刷新；运行 `mvn -f api/pom.xml -Dtest=JobMatchServiceTest test` 和前端构建。

### Task 13: 收藏与投递追踪

**Files:** Create `api/src/main/java/com/fieldward/jobs/ApplicationService.java`、`ApplicationController.java`、`ApplicationServiceTest.java`、`web/src/views/ApplicationsView.vue`；Modify `JobDetailView.vue`、`MentorTurns.java`。

**Interfaces:** `favorite(accountId, jobId, enabled): void`；`updateProgress(accountId, jobId, status, requestId): ApplicationView`。

- [ ] 13.1 测试收藏、选择已有简历版本并跳转来源，以及积分耗尽后手动更新投递进度。
- [ ] 13.2 测试 Mentor 从明确的学生消息提取岗位与进展后直接更新并告知，岗位不明确时询问；重复消息不重复生成记录。
- [ ] 13.3 运行 `mvn -f api/pom.xml -Dtest=ApplicationServiceTest test` 和前端构建。

## 阶段 D — 管理、成本和验收

### Task 14: 管理后台与积分页面

**Files:** Modify `api/src/main/java/com/fieldward/account/AdminController.java`、`web/src/views/AdminUsersView.vue`、`web/src/router.ts`；Create `api/src/main/java/com/fieldward/account/AdminMetricsController.java`、`AdminAccessTest.java`、`web/src/views/AdminRoutesView.vue`、`CreditsView.vue`。

**Interfaces:** `GET /api/credits` 返回余额、阶段预留、规则和流水；管理员接口提供用户、角色、密码重置、启停、删除、积分调整及汇总指标。

- [ ] 14.1 测试普通账号访问所有管理写接口均被拒绝，管理员调整积分有操作者和原因；用户只能查看自己的流水。
- [ ] 14.2 完成岗位管理、方向及基础路线管理、用户管理和注册/诊断/学习/面试/岗位使用看板。
- [ ] 14.3 运行 `mvn -f api/pom.xml -Dtest=AdminAccessTest test` 与前端构建；评估邮箱验证码依赖并记录 MVP 决策。

### Task 15: 端到端验收与发布准备

**Files:** Create `api/src/test/java/com/fieldward/acceptance/MvpFlowTest.java`、`docs/superpowers/plans/fieldward-mvp-acceptance-results.md`；Modify `openspec/changes/redesign-mvp/tasks.md`。

**Interfaces:** 沿用前述服务接口和浏览器入口；所有外部供应商可由测试适配器替换，真实费用测量另行记录。

- [ ] 15.1 用合成用户验收零基础流程：姓名学校、方向探索、通用学习、选择方向、诊断、路线、所有岗位匹配。
- [ ] 15.2 用合成用户验收已有项目流程：上传简历、诊断、选岗位面试、面经复习、自动简历版本更新。
- [ ] 15.3 验收方向确认、断线恢复、积分用尽、路线调整及历史用户兼容；检查账号隔离与媒体清理。
- [ ] 15.4 运行 `mvn -f api/pom.xml test`、`npm --prefix web run build`、`openspec validate redesign-mvp --strict`；记录成本、浏览器兼容、刷新延迟和未达标项。未通过的项目保持未完成。
