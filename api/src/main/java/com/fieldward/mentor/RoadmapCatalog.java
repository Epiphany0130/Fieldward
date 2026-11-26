package com.fieldward.mentor;

import java.util.Arrays;
import java.util.List;

final class RoadmapCatalog {
    private RoadmapCatalog() {}

    static Route forDirection(String direction) {
        return switch (direction) {
            case "java-backend" -> new Route("https://www.itheima.com/special/hmjavaeezly/", List.of(
                    phase("Java 语言基础", """
                            JDK 与 IntelliJ IDEA|Java 基础语法|条件与循环|数组|面向对象|抽象类与接口|String 与常用 API|日期与时间|异常处理
                            |集合与 Map|泛型|内部类|Lambda 与方法引用|Stream API|File 与 IO|Hutool|多线程与线程池|网络编程|面向对象设计与重构
                            """),
                    phase("Web 与关系数据库", """
                            HTML|CSS|JavaScript|Ajax 与 Axios|Vue 3|Element Plus|Git|Maven|HTTP|Servlet|MySQL 表设计与 DDL
                            |SQL 查询与 DML|关联查询|MySQL 索引|数据库事务
                            """),
                    phase("企业级单体开发", """
                            Spring IoC 与依赖注入|Spring AOP|Spring MVC|Spring Boot|REST API|参数校验与异常响应|MyBatis|MyBatis-Plus
                            |JWT|Servlet Filter|Spring 拦截器|文件上传|Redis 与缓存|Linux|Docker|Nginx|单元测试
                            |Spring Security 与 RBAC|对象存储|Jenkins 与 CI/CD
                            """),
                    phase("企业项目与物联网", """
                            需求分析与系统设计|若依框架|MQTT|物联网数据接入|时序数据库|多租户与数据隔离|线上故障排查
                            """),
                    phase("微服务与分布式系统", """
                            Spring Cloud Alibaba|Nacos|OpenFeign|Spring Cloud Gateway|Sentinel|RabbitMQ|RocketMQ|Elasticsearch
                            |Spring Cloud Alibaba AI|Redis 分布式缓存|Redis 高可用|多级缓存|分布式锁|Seata 与分布式事务
                            |OAuth 2.0|Spring Cloud Stream|XXL-JOB|幂等设计|链路追踪|规则引擎|第三方支付接入|地理位置搜索
                            |Kubernetes|服务部署与性能优化
                            """),
                    phase("AI 智能体实战", """
                            大语言模型 API|Spring AI|Spring AI Advisors|提示词设计|结构化输出|流式输出|对话与上下文记忆
                            |向量检索|RAG|模型工具调用|MCP|AI Agent|多智能体工作流|多模态应用|应用上线与评估
                            """),
                    phase("大模型进阶", """
                            Python 基础|LangChain|Prompt Tuning|PEFT 与 LoRA|模型量化|LLaMA Factory|vLLM|Flask 服务化|Stable Diffusion
                            """)));
            case "web-frontend" -> new Route("https://xm.itheima.com/taught/taughtweb.html", List.of(
                    phase("网页与布局", "HTML|CSS|Flexbox|Grid 布局|响应式布局|移动端适配"),
                    phase("JavaScript 与浏览器", """
                            JavaScript 基础|DOM 与事件|ES6+|闭包与原型|Promise|async 与 await|事件循环|HTTP|Axios|浏览器存储
                            """),
                    phase("前端工程化", "Git|Node.js|npm|模块化|TypeScript 基础|TypeScript 泛型与类型声明|Webpack|Vite|前端调试"),
                    phase("框架与项目", """
                            Vue 2|Vue 3|Vue Router|Pinia|Element Plus|React|React Hooks|React Router|Redux|组件设计|图表与数据可视化
                            |前端权限|前端性能优化
                            """),
                    phase("移动端", "微信小程序|小程序组件与生命周期|小程序网络请求|小程序分包|uni-app|移动端项目交付")));
            case "python-ai" -> new Route("https://ai.itheima.com/", List.of(
                    phase("Python 与数据处理", """
                            Python 基础|Python 面向对象|文件与异常处理|Linux|Git|SQL|NumPy|Pandas|Matplotlib|数据清洗与特征工程
                            """),
                    phase("机器学习", """
                            监督与无监督学习|线性与逻辑回归|KNN|朴素贝叶斯|决策树|随机森林|集成学习|K-means|模型评估与交叉验证|Scikit-learn
                            """),
                    phase("深度学习与 NLP", """
                            PyTorch|神经网络与反向传播|CNN|RNN 与 LSTM|视觉识别|目标检测|文本预处理|词嵌入|注意力机制|Transformer
                            """),
                    phase("大模型工程", """
                            大语言模型 API|提示词设计|向量数据库|LangChain|RAG|文档切分与召回|重排与评估|工具调用|MCP|AI Agent
                            |模型微调与 LoRA|FastAPI|模型服务部署
                            """)));
            case "data-engineering" -> new Route("https://www.itheima.com/bigdata/", List.of(
                    phase("数据与编程基础", "Linux|Python|SQL|MySQL|数据清洗|ETL|Superset"),
                    phase("Hadoop 生态与离线数仓", """
                            Hadoop|HDFS|MapReduce|YARN|ZooKeeper|Hive|Hive SQL|数据仓库分层|维度建模|全量与增量同步|任务调度
                            """),
                    phase("Spark", "Spark Core|RDD|Spark SQL|DataFrame|Spark 性能调优|Structured Streaming"),
                    phase("实时计算与湖仓", """
                            Kafka|Flink|Flink SQL|Flink 时间语义与水位线|Flink 状态与 Checkpoint|Flink CDC|Paimon
                            |StarRocks|实时数仓|湖仓一体|数据质量与监控
                            """),
                    phase("综合项目", "离线数仓指标开发|实时数据大屏|数据链路运维|大模型数据开发")));
            case "software-testing" -> new Route("https://yun.itheima.com/subject/testmap/index.html", List.of(
                    phase("软件测试基础", """
                            软件研发与测试流程|需求分析|测试计划|等价类与边界值|判定表与场景法|测试用例设计|缺陷管理|回归测试|敏捷测试
                            """),
                    phase("测试工具与接口", """
                            Linux|MySQL|SQL 查询|HTTP|浏览器开发者工具|抓包与日志分析|Postman|接口协议与鉴权|接口测试数据管理
                            """),
                    phase("测试自动化", """
                            Python 基础|Requests|pytest|测试夹具与参数化|Selenium|Appium|移动端测试|测试报告|Git|Jenkins|持续集成
                            """),
                    phase("性能与项目", "JMeter|负载与压力测试|性能指标与瓶颈定位|测试环境与发布|AI 辅助用例设计|大模型应用测试")));
            case "cloud-ops" -> new Route("https://linux.itheima.com/", List.of(
                    phase("系统与网络", """
                            Linux 命令与文件系统|用户与权限|进程与服务管理|磁盘与存储|TCP/IP|DNS|HTTP 与 HTTPS|SSH|Shell 脚本
                            """),
                    phase("应用运维", """
                            Nginx|反向代理与负载均衡|MySQL 运维|Redis 运维|日志分析|备份与恢复|高可用|故障定位|Ansible
                            """),
                    phase("云原生与交付", """
                            Git|Docker 镜像与容器|Docker Compose|镜像仓库|Kubernetes Pod 与 Deployment|Kubernetes Service 与 Ingress
                            |ConfigMap 与 Secret|持久卷|Helm|Jenkins|CI/CD|发布与回滚
                            """),
                    phase("可观测与云平台", "Prometheus|Grafana|集中日志|告警与值班|云主机与网络|云存储|资源与权限管理|AI 应用服务运维")));
            case "harmony-app" -> new Route("https://hmkf.itheima.com/", List.of(
                    phase("编程与平台基础", "HarmonyOS NEXT|DevEco Studio|TypeScript|ArkTS 基础|ArkTS 面向对象|Git|Hvigor"),
                    phase("鸿蒙原生应用", """
                            ArkUI 声明式 UI|组件与布局|状态管理|页面路由|UIAbility 生命周期|窗口与页面适配|资源管理
                            |HTTP 网络请求|Preferences|关系型数据库|权限管理|媒体与文件|应用签名与发布
                            """),
                    phase("工程与综合项目", "HAR 与模块化|组件复用|异常处理与日志|单元测试|卡顿与性能优化|多端协同|应用项目实战"),
                    phase("全栈与多端", "HTML 与 CSS|JavaScript|Vue 3|React|Taro|原生微信小程序|微前端|Node.js 接口服务")));
            case "embedded-robotics" -> new Route("https://jcdl.itheima.com/", List.of(
                    phase("嵌入式基础", """
                            C 语言|C++|指针与内存|数据结构|Linux 命令|GCC 与 GDB|Makefile|CMake|Git|电路基础|数字与模拟信号
                            """),
                    phase("微控制器与实时系统", """
                            STM32|GPIO|中断与定时器|PWM|ADC|UART|I2C|SPI|CAN|传感器与执行器|FreeRTOS|任务调度与同步
                            """),
                    phase("嵌入式 Linux", """
                            交叉编译|U-Boot|Linux 内核|设备树|根文件系统|字符设备驱动|进程与线程|进程间通信|Socket
                            |Qt 应用开发|系统调试与部署
                            """),
                    phase("机器人应用", "ROS 2 节点与话题|坐标变换|运动控制|传感器融合|SLAM|路径规划与导航|OpenCV|机器人项目调试")));
            default -> throw new IllegalArgumentException("Unknown career direction " + direction);
        };
    }

    private static Phase phase(String title, String topics) {
        return new Phase(title, Arrays.stream(topics.split("\\|")).map(String::strip).toList());
    }

    record Route(String sourceUrl, List<Phase> phases) {
        List<String> topics() {
            return phases.stream().flatMap(phase -> phase.topics().stream()).toList();
        }
    }

    record Phase(String title, List<String> topics) {}
}
