CREATE TABLE career_directions (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    roles TEXT NOT NULL
);

CREATE TABLE career_capabilities (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL
);

CREATE TABLE career_route_versions (
    direction_id TEXT NOT NULL REFERENCES career_directions (id),
    version INTEGER NOT NULL CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (direction_id, version)
);

CREATE TABLE career_route_items (
    direction_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    position INTEGER NOT NULL CHECK (position > 0),
    capability_id TEXT NOT NULL REFERENCES career_capabilities (id),
    legacy_skill_id TEXT,
    title TEXT NOT NULL,
    objective TEXT NOT NULL,
    PRIMARY KEY (direction_id, version, capability_id),
    UNIQUE (direction_id, version, position),
    UNIQUE (direction_id, version, legacy_skill_id),
    FOREIGN KEY (direction_id, version) REFERENCES career_route_versions (direction_id, version)
);

CREATE TABLE capability_evidence (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    capability_id TEXT NOT NULL REFERENCES career_capabilities (id),
    source_type TEXT NOT NULL CHECK (source_type IN
        ('SELF_REPORT','DIAGNOSIS','LEARNING','INTERVIEW','LEGACY_ASSESSMENT','LEGACY_VERIFICATION')),
    source_id TEXT NOT NULL CHECK (btrim(source_id) <> ''),
    outcome TEXT NOT NULL CHECK (outcome IN ('PENDING','PASSED','NEEDS_PRACTICE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_type <> 'SELF_REPORT' OR outcome = 'PENDING'),
    UNIQUE (account_id, capability_id, source_type, source_id)
);

-- Freeze the initial catalog in this migration. Subsequent route versions retain stable capability IDs.
INSERT INTO career_directions (id, title, description, roles) VALUES
    ('java-backend', 'Java 后端开发', '开发业务接口、数据服务和后台系统。', 'Java 开发实习生 · 后端开发工程师'),
    ('web-frontend', 'Web 前端开发', '实现网页交互、业务组件和前端工程化。', 'Web 前端实习生 · 前端开发工程师'),
    ('python-ai', 'Python 与 AI 应用', '构建面向业务的模型应用、知识库和评测服务。', 'AI 应用实习生 · 大模型应用开发工程师'),
    ('data-engineering', '大数据开发', '搭建数据采集、计算与数据仓库链路。', '大数据开发实习生 · 数据工程师'),
    ('software-testing', '软件测试与自动化', '设计验证方案，建设接口、页面和回归测试。', '测试开发实习生 · 自动化测试工程师'),
    ('cloud-ops', '云计算与智能运维', '保障系统部署、监控和故障恢复。', '运维开发实习生 · 云平台工程师'),
    ('harmony-app', '鸿蒙应用开发', '开发 HarmonyOS 应用界面、交互和系统能力。', '鸿蒙开发实习生 · 移动应用工程师'),
    ('embedded-robotics', '嵌入式与机器人', '从软硬件接口到设备控制和机器人应用。', '嵌入式实习生 · 机器人软件开发工程师');

INSERT INTO career_capabilities (id, title) VALUES
    ('java-backend:java', 'Java 与集合'),
    ('java-backend:api', 'HTTP 与 Spring Boot'),
    ('sql', 'SQL'),
    ('java-backend:redis', 'Redis 与缓存'),
    ('java-backend:delivery', 'Git、测试与交付'),
    ('web-frontend:web', 'HTML、CSS 与响应式'),
    ('web-frontend:typescript', 'JavaScript 与 TypeScript'),
    ('web-frontend:framework', 'Vue 或 React'),
    ('web-frontend:integration', '接口联调与页面状态'),
    ('web-frontend:tooling', '构建、调试与测试'),
    ('python-ai:python', 'Python 与数据处理'),
    ('python-ai:model-api', '模型 API 与结构化输出'),
    ('python-ai:retrieval', '知识检索与 RAG'),
    ('python-ai:serving', 'Web API 与服务部署'),
    ('python-ai:evaluation', '评测与可靠性'),
    ('data-engineering:coding', 'Python 或 Java 数据处理'),
    ('data-engineering:pipeline', 'ETL 与数据仓库'),
    ('data-engineering:distributed', 'Spark 或 Flink'),
    ('data-engineering:quality', '调度、质量与监控'),
    ('software-testing:cases', '测试设计与缺陷复现'),
    ('software-testing:api-test', 'HTTP 与接口测试'),
    ('software-testing:automation', '自动化脚本'),
    ('software-testing:data', 'SQL 与测试数据'),
    ('software-testing:quality', '持续集成与质量反馈'),
    ('cloud-ops:linux', 'Linux 与脚本'),
    ('cloud-ops:network', '网络与服务排障'),
    ('cloud-ops:containers', 'Docker 与容器部署'),
    ('cloud-ops:ci', 'CI/CD 与发布'),
    ('cloud-ops:observability', '监控与故障响应'),
    ('harmony-app:arkts', 'ArkTS 与类型基础'),
    ('harmony-app:arkui', 'ArkUI 与状态管理'),
    ('harmony-app:platform', 'Ability 与应用生命周期'),
    ('harmony-app:data', '网络请求与本地存储'),
    ('harmony-app:quality', '设备调试与测试'),
    ('embedded-robotics:cpp', 'C/C++ 与内存基础'),
    ('embedded-robotics:device', '微控制器与外设'),
    ('embedded-robotics:systems', '嵌入式 Linux 或 RTOS'),
    ('embedded-robotics:integration', '设备集成或 ROS 2'),
    ('embedded-robotics:debugging', '调试与硬件验证');

INSERT INTO career_route_versions (direction_id, version) SELECT id, 1 FROM career_directions;

INSERT INTO career_route_items (direction_id, version, position, capability_id, legacy_skill_id, title, objective) VALUES
    ('java-backend', 1, 1, 'java-backend:java', 'java', 'Java 与集合', '用 Java 实现一段可测试的业务逻辑。'),
    ('java-backend', 1, 2, 'java-backend:api', 'api', 'HTTP 与 Spring Boot', '设计并实现有明确校验与错误响应的 HTTP 接口。'),
    ('java-backend', 1, 3, 'sql', 'sql', '关系数据库与 SQL', '能设计表结构并解释查询与索引。'),
    ('java-backend', 1, 4, 'java-backend:redis', 'redis', 'Redis 与缓存', '理解缓存命中、过期和回源。'),
    ('java-backend', 1, 5, 'java-backend:delivery', 'delivery', 'Git、测试与交付', '能提交变更并复现关键测试。'),
    ('web-frontend', 1, 1, 'web-frontend:web', 'web', 'HTML、CSS 与响应式', '能搭建有语义、可在不同屏幕使用的页面。'),
    ('web-frontend', 1, 2, 'web-frontend:typescript', 'typescript', 'JavaScript 与 TypeScript', '掌握事件、异步与常用类型。'),
    ('web-frontend', 1, 3, 'web-frontend:framework', 'framework', 'Vue 或 React', '能完成组件拆分、数据流与状态更新。'),
    ('web-frontend', 1, 4, 'web-frontend:integration', 'integration', '接口联调与页面状态', '处理加载、空数据、错误和表单校验。'),
    ('web-frontend', 1, 5, 'web-frontend:tooling', 'tooling', '构建、调试与测试', '能定位常见前端故障并打包交付。'),
    ('python-ai', 1, 1, 'python-ai:python', 'python', 'Python 与数据处理', '编写可复用的 Python 逻辑并处理异常。'),
    ('python-ai', 1, 2, 'python-ai:model-api', 'model-api', '模型 API 与结构化输出', '能把模型调用封装成可校验的服务。'),
    ('python-ai', 1, 3, 'python-ai:retrieval', 'retrieval', '知识检索与 RAG', '能拆分文档、检索并引用原文。'),
    ('python-ai', 1, 4, 'python-ai:serving', 'serving', 'Web API 与服务部署', '能把 AI 功能包装成可运行的 Web 服务。'),
    ('python-ai', 1, 5, 'python-ai:evaluation', 'evaluation', '评测与可靠性', '能比较输出质量并识别错误。'),
    ('data-engineering', 1, 1, 'sql', 'sql', 'SQL 与数据分析', '能完成关联、聚合和窗口统计。'),
    ('data-engineering', 1, 2, 'data-engineering:coding', 'coding', 'Python 或 Java 数据处理', '能清洗、校验并稳定处理批数据。'),
    ('data-engineering', 1, 3, 'data-engineering:pipeline', 'pipeline', 'ETL 与数据仓库', '设计可追溯数据链路及主题表。'),
    ('data-engineering', 1, 4, 'data-engineering:distributed', 'distributed', 'Spark 或 Flink', '能运行一个分布式批处理或流处理任务。'),
    ('data-engineering', 1, 5, 'data-engineering:quality', 'quality', '调度、质量与监控', '检测断流、重复和口径变化。'),
    ('software-testing', 1, 1, 'software-testing:cases', 'cases', '测试设计与缺陷复现', '能覆盖正常、边界和失败路径。'),
    ('software-testing', 1, 2, 'software-testing:api-test', 'api-test', 'HTTP 与接口测试', '能校验接口响应、鉴权和错误。'),
    ('software-testing', 1, 3, 'software-testing:automation', 'automation', '自动化脚本', '能编写稳定可复用的自动化测试。'),
    ('software-testing', 1, 4, 'software-testing:data', 'data', 'SQL 与测试数据', '能准备隔离数据并核对写库结果。'),
    ('software-testing', 1, 5, 'software-testing:quality', 'quality', '持续集成与质量反馈', '能自动执行测试并解释结果。'),
    ('cloud-ops', 1, 1, 'cloud-ops:linux', 'linux', 'Linux 与脚本', '能操作系统并编写可复用脚本。'),
    ('cloud-ops', 1, 2, 'cloud-ops:network', 'network', '网络与服务排障', '能从 DNS、端口到 HTTP 分层定位。'),
    ('cloud-ops', 1, 3, 'cloud-ops:containers', 'containers', 'Docker 与容器部署', '能构建镜像并配置容器运行。'),
    ('cloud-ops', 1, 4, 'cloud-ops:ci', 'ci', 'CI/CD 与发布', '做可复现发布与回滚。'),
    ('cloud-ops', 1, 5, 'cloud-ops:observability', 'observability', '监控与故障响应', '能用指标和日志缩小故障范围。'),
    ('harmony-app', 1, 1, 'harmony-app:arkts', 'arkts', 'ArkTS 与类型基础', '用类型约束和异步编写应用逻辑。'),
    ('harmony-app', 1, 2, 'harmony-app:arkui', 'arkui', 'ArkUI 与状态管理', '完成页面布局、交互和状态更新。'),
    ('harmony-app', 1, 3, 'harmony-app:platform', 'platform', 'Ability 与应用生命周期', '理解应用启动、页面导航和生命周期。'),
    ('harmony-app', 1, 4, 'harmony-app:data', 'data', '网络请求与本地存储', '实现可离线恢复的小功能。'),
    ('harmony-app', 1, 5, 'harmony-app:quality', 'quality', '设备调试与测试', '能在模拟或真实设备上定位问题。'),
    ('embedded-robotics', 1, 1, 'embedded-robotics:cpp', 'cpp', 'C/C++ 与内存基础', '理解指针、生命周期和常用数据结构。'),
    ('embedded-robotics', 1, 2, 'embedded-robotics:device', 'device', '微控制器与外设', '读取传感器数据并排查基本通信。'),
    ('embedded-robotics', 1, 3, 'embedded-robotics:systems', 'systems', '嵌入式 Linux 或 RTOS', '理解任务、基本调度与设备运行环境。'),
    ('embedded-robotics', 1, 4, 'embedded-robotics:integration', 'integration', '设备集成或 ROS 2', '连接多个组件并验证数据或消息路径。'),
    ('embedded-robotics', 1, 5, 'embedded-robotics:debugging', 'debugging', '调试与硬件验证', '能复现并解释软硬件故障。');
