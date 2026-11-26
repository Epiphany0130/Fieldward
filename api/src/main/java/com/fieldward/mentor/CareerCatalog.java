package com.fieldward.mentor;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CareerCatalog {
    static final String SOURCE = "https://go.itheima.com/flow/flow.html";
    static final int INTERVIEW_THRESHOLD = 75;

    private CareerCatalog() {}

    static final List<Direction> DIRECTIONS = List.of(
        direction("java-backend", "Java 后端开发", "开发业务接口、数据服务和后台系统。", "Java 开发实习生 · 后端开发工程师",
            skill("java", "Java 与集合", 22, true, "用 Java 实现一段可测试的业务逻辑。",
                "用 Map 统计重复值并覆盖空输入。", "如果要统计一组名字各出现多少次，你会选哪种 Java 集合来记录？"),
            skill("api", "HTTP 与 Spring Boot", 25, true, "设计并实现有明确校验与错误响应的 HTTP 接口。",
                "实现一组 Spring Boot 查询和新增接口，并记录输入输出。", "如果要新增一条借阅记录，你会选什么 HTTP 请求方法？"),
            skill("sql", "关系数据库与 SQL", 22, true, "能设计表结构并解释查询与索引。",
                "为借阅系统建表，写一条联表查询并用 EXPLAIN 查看访问路径。", "如果要查出每条借阅记录对应的书名，你会用哪个字段把两张表连起来？"),
            skill("redis", "Redis 与缓存", 15, false, "理解缓存命中、过期和回源。",
                "给热点查询加缓存并报告命中和未命中的结果。", "如果缓存里没有查到数据，你会怎么返回正确结果？"),
            skill("delivery", "Git、测试与交付", 16, false, "能提交变更并复现关键测试。",
                "用 Git 保存一项小功能，补上测试和启动说明。", "你通常怎么确认一个接口改完之后还能正常工作？")),
        direction("web-frontend", "Web 前端开发", "实现网页交互、业务组件和前端工程化。", "Web 前端实习生 · 前端开发工程师",
            skill("web", "HTML、CSS 与响应式", 20, true, "能搭建有语义、可在不同屏幕使用的页面。",
                "实现响应式列表页，检查手机布局和键盘操作。", "两列列表在手机上放不下时，你会怎么调整布局？"),
            skill("typescript", "JavaScript 与 TypeScript", 24, true, "掌握事件、异步与常用类型。",
                "写一个带类型、能处理成功和失败的异步请求。", "一个异步请求失败时，你会在哪里捕获错误？"),
            skill("framework", "Vue 或 React", 24, true, "能完成组件拆分、数据流与状态更新。",
                "用 Vue 或 React 做一个可查找、可筛选的列表。", "如果筛选框和列表是不同组件，你会把筛选条件放在哪里？"),
            skill("integration", "接口联调与页面状态", 17, false, "处理加载、空数据、错误和表单校验。",
                "为接口驱动的页面实现加载、空结果和重试状态。", "一个列表请求失败时，你希望用户在界面上看到什么？"),
            skill("tooling", "构建、调试与测试", 15, false, "能定位常见前端故障并打包交付。",
                "用浏览器工具排查一次请求问题，补测试并打包。", "浏览器里页面没有数据时，你会从哪里开始定位？")),
        direction("python-ai", "Python 与 AI 应用", "构建面向业务的模型应用、知识库和评测服务。", "AI 应用实习生 · 大模型应用开发工程师",
            skill("python", "Python 与数据处理", 22, true, "编写可复用的 Python 逻辑并处理异常。",
                "编写脚本清洗一份小数据，并验证空值处理。", "你用 Python 处理过什么数据？"),
            skill("model-api", "模型 API 与结构化输出", 23, true, "能把模型调用封装成可校验的服务。",
                "实现一个返回 JSON 的模型工具，覆盖超时和格式异常。", "如果模型偶尔返回无效 JSON，你会怎么处理？"),
            skill("retrieval", "知识检索与 RAG", 23, true, "能拆分文档、检索并引用原文。",
                "用一组文档完成最小 RAG 流程，用三道题验证引用。", "给你三篇文档，你会怎么找出与一个问题最相关的段落？"),
            skill("serving", "Web API 与服务部署", 16, false, "能把 AI 功能包装成可运行的 Web 服务。",
                "用 FastAPI 暴露一个接口并记录日志和错误。", "模型请求超时时，你希望自己的接口给调用方什么反馈？"),
            skill("evaluation", "评测与可靠性", 16, false, "能比较输出质量并识别错误。",
                "为应用准备正例、缺资料和提示注入的验证集。", "你会怎么检验模型有没有引用错资料？")),
        direction("data-engineering", "大数据开发", "搭建数据采集、计算与数据仓库链路。", "大数据开发实习生 · 数据工程师",
            skill("sql", "SQL 与数据分析", 24, true, "能完成关联、聚合和窗口统计。",
                "按用户统计近七天事件，并解释一条窗口查询。", "你做过按日期分组的 SQL 统计吗？"),
            skill("coding", "Python 或 Java 数据处理", 20, true, "能清洗、校验并稳定处理批数据。",
                "写一个去重入库脚本，记录异常数据。", "如果日志里有重复记录，你会用什么字段去重？"),
            skill("pipeline", "ETL 与数据仓库", 24, true, "设计可追溯数据链路及主题表。",
                "从样例日志抽取事实表，并画出从原始数据到报表的过程。", "你处理过从原始文件到报表的链路吗？"),
            skill("distributed", "Spark 或 Flink", 18, false, "能运行一个分布式批处理或流处理任务。",
                "用 Spark 或 Flink 按窗口统计小数据集并检查输出。", "统计一批日志时，哪个步骤可以先分给多个任务并行做？"),
            skill("quality", "调度、质量与监控", 14, false, "检测断流、重复和口径变化。",
                "增加数据量和主键质量检查，记录一次失败重跑。", "你怎样发现一条定时数据任务漏跑了？")),
        direction("software-testing", "软件测试与自动化", "设计验证方案，建设接口、页面和回归测试。", "测试开发实习生 · 自动化测试工程师",
            skill("cases", "测试设计与缺陷复现", 25, true, "能覆盖正常、边界和失败路径。",
                "为登录功能写出边界和异常用例，并写一份可复现缺陷报告。", "让你测试一个登录页面，你先覆盖哪种失败场景？"),
            skill("api-test", "HTTP 与接口测试", 23, true, "能校验接口响应、鉴权和错误。",
                "用测试工具覆盖一个接口的成功、越权和缺参数请求。", "如果接口需要登录，你会如何构造一个鉴权失败用例？"),
            skill("automation", "自动化脚本", 22, true, "能编写稳定可复用的自动化测试。",
                "用 pytest、Playwright 或同类工具跑通一次可复现的回归。", "如果只能自动化一个重复测试，你会选哪个用例？"),
            skill("data", "SQL 与测试数据", 16, false, "能准备隔离数据并核对写库结果。",
                "为一组用例准备数据，并用 SQL 验证写入和清理。", "接口返回成功时，你会怎样核对数据真的写进了库？"),
            skill("quality", "持续集成与质量反馈", 14, false, "能自动执行测试并解释结果。",
                "把一组回归测试加入 CI 并输出可阅读的结果。", "怎样解决测试脚本在本机通过、在 CI 上失败的问题？")),
        direction("cloud-ops", "云计算与智能运维", "保障系统部署、监控和故障恢复。", "运维开发实习生 · 云平台工程师",
            skill("linux", "Linux 与脚本", 24, true, "能操作系统并编写可复用脚本。",
                "用命令定位资源占用，写一段带退出码的检查脚本。", "一个 Linux 进程占用 CPU 很高，你会先用什么命令看？"),
            skill("network", "网络与服务排障", 23, true, "能从 DNS、端口到 HTTP 分层定位。",
                "模拟一次服务无法访问，记录每层排查证据。", "服务显示启动了但访问失败，你会先检查哪一层？"),
            skill("containers", "Docker 与容器部署", 23, true, "能构建镜像并配置容器运行。",
                "用 Docker 运行一个 Web 服务，验证健康检查和端口。", "你会如何确认容器服务的端口已经映射到宿主机？"),
            skill("ci", "CI/CD 与发布", 15, false, "做可复现发布与回滚。",
                "搭一个从构建到发布的流水线，并写出回滚步骤。", "上线后服务报错，你准备先核对哪项发布信息？"),
            skill("observability", "监控与故障响应", 15, false, "能用指标和日志缩小故障范围。",
                "采集一项错误率指标，触发告警并记录一次排查。", "请求延迟突然升高，你会查哪一个指标？")),
        direction("harmony-app", "鸿蒙应用开发", "开发 HarmonyOS 应用界面、交互和系统能力。", "鸿蒙开发实习生 · 移动应用工程师",
            skill("arkts", "ArkTS 与类型基础", 24, true, "用类型约束和异步编写应用逻辑。",
                "写一个 ArkTS 异步数据服务，覆盖空结果。", "如果接口可能返回一条记录或空结果，你会怎样描述返回类型？"),
            skill("arkui", "ArkUI 与状态管理", 25, true, "完成页面布局、交互和状态更新。",
                "用 ArkUI 实现可筛选列表和详情页。", "用户在列表里添加一项时，你会让哪个状态驱动页面更新？"),
            skill("platform", "Ability 与应用生命周期", 21, true, "理解应用启动、页面导航和生命周期。",
                "实现两个页面跳转并记录进入、离开时的行为。", "用户从列表打开详情再返回时，你会保留哪项列表状态？"),
            skill("data", "网络请求与本地存储", 16, false, "实现可离线恢复的小功能。",
                "保存一份小列表，并在离线时载入。", "如果没有网络，你想让用户看到上次数据还是空白页面？"),
            skill("quality", "设备调试与测试", 14, false, "能在模拟或真实设备上定位问题。",
                "在设备或模拟器复现一个故障，采集日志并提交修复。", "页面在真机上表现不同时，你会采集哪些信息？")),
        direction("embedded-robotics", "嵌入式与机器人", "从软硬件接口到设备控制和机器人应用。", "嵌入式实习生 · 机器人软件开发工程师",
            skill("cpp", "C/C++ 与内存基础", 24, true, "理解指针、生命周期和常用数据结构。",
                "实现定长缓冲并写出越界和空缓冲测试。", "向定长数组写入前，你会检查哪个条件来防止越界？"),
            skill("device", "微控制器与外设", 23, true, "读取传感器数据并排查基本通信。",
                "在开发板或仿真器上读取传感器并记录波形或输出。", "连接开发板串口后，你会怎样确认设备正在发送数据？"),
            skill("systems", "嵌入式 Linux 或 RTOS", 23, true, "理解任务、基本调度与设备运行环境。",
                "运行两个可观测任务并解释其中一个延迟的原因。", "实时任务突然延迟增大时，你会先检查什么？"),
            skill("integration", "设备集成或 ROS 2", 16, false, "连接多个组件并验证数据或消息路径。",
                "接通两个模拟节点，验证订阅和消息异常处理。", "两个设备组件交换数据时，怎么确认消息没有漏掉？"),
            skill("debugging", "调试与硬件验证", 14, false, "能复现并解释软硬件故障。",
                "构造一次可复现故障，记录日志并验证修复。", "遇到设备偶发异常时，你会先留下什么日志？"))
    );

    static Direction require(String id) {
        return DIRECTIONS.stream().filter(direction -> direction.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个有效的职业方向"));
    }

    private static Direction direction(String id, String title, String description, String roles, Skill... skills) {
        return new Direction(id, title, description, roles, List.of(skills));
    }

    private static Skill skill(String id, String title, int weight, boolean core, String objective,
                               String task, String question) {
        return new Skill(id, title, weight, core, objective, task, question);
    }

    record Direction(String id, String title, String description, String roles, List<Skill> skills) {}
    record Skill(String id, String title, int weight, boolean core, String objective, String task, String question) {}
}
