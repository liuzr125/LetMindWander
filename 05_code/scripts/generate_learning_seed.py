#!/usr/bin/env python3
"""Generate an idempotent MySQL 5.7 seed for durable learning cards.

The generated SQL never drops or recreates a table. IDs and hashes are deterministic,
so rerunning the script updates the same seed records instead of duplicating them.
"""
from hashlib import sha256
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "backend" / "sql" / "V3.2_today_learning_seed.sql"
ADMIN_ID = "00000000000000000000000000000002"

TOPICS = {
    "计算机基础": ("NIST 计算机安全资源中心术语表", "NIST", "https://csrc.nist.gov/glossary", [
        ("二进制与位", "计算机用 0 和 1 表示状态，位是信息表示的最小单位。"),
        ("字节与字符编码", "字节是常见存储单位，字符编码规定字符与字节序列之间的映射。"),
        ("整数与浮点数", "整数和浮点数采用不同表示方式，精度、范围与舍入行为也不同。"),
        ("补码表示", "补码让有符号整数的加减法可以复用统一的二进制运算电路。"),
        ("CPU 指令周期", "处理器反复执行取指、译码、执行和写回，完成程序指令。"),
        ("进程与线程", "进程提供资源隔离，线程是进程内可被调度的执行单元。"),
        ("用户态与内核态", "权限级别隔离普通程序与操作系统核心，降低错误扩散风险。"),
        ("虚拟内存", "虚拟内存为进程提供连续地址空间，并通过页表映射物理内存。"),
        ("栈与堆", "栈常用于函数调用和局部变量，堆用于生命周期更灵活的动态对象。"),
        ("文件系统", "文件系统组织持久化数据，并维护名称、权限、目录和元数据。"),
        ("时间复杂度", "时间复杂度描述输入规模增长时算法执行步骤的增长趋势。"),
        ("空间复杂度", "空间复杂度描述算法除输入外所需内存随规模增长的趋势。"),
        ("数组", "数组把同类元素连续组织，支持按下标快速访问。"),
        ("链表", "链表用节点引用连接数据，插入灵活但随机访问成本较高。"),
        ("栈数据结构", "栈遵循后进先出，适合调用管理、撤销和深度优先搜索。"),
        ("队列数据结构", "队列遵循先进先出，适合任务排队与广度优先搜索。"),
        ("哈希表", "哈希表通过哈希函数定位桶，在良好分布下提供近似常数时间访问。"),
        ("树与二叉树", "树表达层级关系，二叉树限制每个节点最多有两个子节点。"),
        ("图结构", "图由顶点和边构成，适合表达网络、依赖和路径问题。"),
        ("TCP 连接", "TCP 提供面向连接、可靠、有序的字节流传输。"),
        ("UDP 数据报", "UDP 面向数据报且不保证到达，适合低延迟或应用自定义可靠性的场景。"),
        ("DNS 解析", "DNS 将域名解析为网络地址，并通过分层缓存提高效率。"),
        ("HTTP 请求响应", "HTTP 以请求和响应交换资源表示，方法与状态码表达语义。"),
        ("进程同步", "锁、信号量和条件变量用于协调并发访问与执行顺序。"),
        ("缓存局部性", "时间局部性和空间局部性解释了缓存为何能显著提升访问性能。"),
    ]),
    "AI基础": ("Stanford CS229 机器学习课程", "Stanford University", "https://cs229.stanford.edu/", [
        ("监督学习", "监督学习从带标签样本中学习输入到目标的映射。"),
        ("无监督学习", "无监督学习从未标注数据中发现结构、分组或低维表示。"),
        ("强化学习", "强化学习通过状态、动作和奖励信号学习长期决策策略。"),
        ("训练集验证集测试集", "分离数据集可避免用测试结果反复调参导致评估失真。"),
        ("特征与标签", "特征描述输入，标签表示监督任务期望预测的目标。"),
        ("损失函数", "损失函数量化预测与目标之间的差异，并为优化提供方向。"),
        ("梯度下降", "梯度下降沿损失函数下降方向迭代更新模型参数。"),
        ("学习率", "学习率控制每次参数更新幅度，过大可能震荡，过小会收敛缓慢。"),
        ("过拟合", "过拟合指模型记住训练细节却不能推广到新样本。"),
        ("欠拟合", "欠拟合指模型或训练不足，连训练数据中的主要规律也未学到。"),
        ("正则化", "正则化通过约束模型复杂度降低过拟合风险。"),
        ("交叉验证", "交叉验证轮换训练与验证划分，更稳健地估计模型表现。"),
        ("线性回归", "线性回归用特征的线性组合预测连续目标。"),
        ("逻辑回归", "逻辑回归将线性得分映射为概率，常用于二分类。"),
        ("决策树", "决策树用一系列特征判断划分样本，规则直观但需控制复杂度。"),
        ("随机森林", "随机森林组合多棵随机化决策树，降低单树方差。"),
        ("支持向量机", "支持向量机寻找最大间隔决策边界，并可借助核函数处理非线性。"),
        ("聚类", "聚类按相似性把样本分组，结果依赖距离、尺度与簇假设。"),
        ("主成分分析", "主成分分析寻找最大方差方向，用于线性降维和去相关。"),
        ("神经网络", "神经网络通过多层可学习变换逼近复杂函数。"),
        ("反向传播", "反向传播利用链式法则高效计算各层参数梯度。"),
        ("激活函数", "激活函数引入非线性，使多层网络能够表达复杂关系。"),
        ("准确率精确率召回率", "不同分类指标关注总体正确、预测可信和漏检程度，不能混用。"),
        ("混淆矩阵", "混淆矩阵展示真实类别与预测类别的组合，是分类误差分析基础。"),
        ("数据泄漏", "数据泄漏让训练过程获得部署时不可用的信息，导致评估虚高。"),
    ]),
    "大模型": ("Attention Is All You Need", "Vaswani 等", "https://arxiv.org/abs/1706.03762", [
        ("Transformer 架构", "Transformer 以注意力和前馈网络为核心，支持高并行度序列建模。"),
        ("自注意力", "自注意力让序列中每个位置按相关性聚合其他位置的信息。"),
        ("查询键值", "注意力用 Query 与 Key 计算权重，再对 Value 做加权汇总。"),
        ("多头注意力", "多头注意力在多个表示子空间并行学习不同关系。"),
        ("位置编码", "位置编码向无递归的注意力模型注入顺序信息。"),
        ("因果掩码", "因果掩码阻止生成模型看到未来 token，保持自回归条件。"),
        ("Token 与分词", "分词器把文本切成模型词表中的 token，影响长度、成本与跨语言表现。"),
        ("词嵌入", "词嵌入把离散 token 映射到连续向量空间。"),
        ("预训练", "预训练从大规模数据学习通用语言模式，为下游适配提供基础。"),
        ("指令微调", "指令微调用任务示例提升模型遵循自然语言要求的能力。"),
        ("偏好对齐", "偏好对齐利用人类或模型偏好信号改善输出行为，但不能替代事实验证。"),
        ("上下文窗口", "上下文窗口限制一次推理可直接处理的 token 数量。"),
        ("上下文学习", "上下文学习通过提示中的示例改变当前任务表现，而不更新模型权重。"),
        ("提示词结构", "清晰的目标、输入、约束与输出格式能减少任务歧义。"),
        ("温度与采样", "温度和采样策略影响生成分布的集中程度与随机性。"),
        ("幻觉", "模型可能生成流畅但无事实依据的内容，关键结论需要外部证据。"),
        ("检索增强生成", "RAG 在生成前检索外部证据，并把证据纳入回答上下文。"),
        ("向量检索", "向量检索按嵌入相似度寻找语义接近的文档片段。"),
        ("文档切分", "切分策略决定检索单元大小，影响召回、上下文完整性与成本。"),
        ("重排序", "重排序模型对初步召回结果再次打分，提高最终上下文相关性。"),
        ("工具调用", "工具调用让模型输出结构化参数，由应用执行外部操作并返回结果。"),
        ("智能体循环", "智能体循环通常包含观察、计划、执行、校验和停止条件。"),
        ("量化", "量化用较低位宽表示权重或激活，降低内存与计算成本。"),
        ("蒸馏", "知识蒸馏让小模型学习大模型输出分布或中间表示。"),
        ("大模型评测", "可靠评测需要明确任务、数据污染、评分标准、置信区间和失败样本。"),
    ]),
    "Java与Spring": ("Spring Framework Reference", "Spring", "https://docs.spring.io/spring-framework/reference/", [
        ("Java 基本类型", "Java 基本类型直接表示数值、字符和布尔值，并有明确位宽或语义。"),
        ("对象与引用", "对象保存在堆中，变量通常保存指向对象的引用。"),
        ("封装继承多态", "面向对象三项核心机制用于控制边界、复用行为和替换实现。"),
        ("接口与抽象类", "接口强调契约，抽象类可同时提供状态与部分实现。"),
        ("异常处理", "受检与非受检异常表达不同失败契约，捕获时应保留上下文。"),
        ("泛型", "泛型在编译期提供类型约束，减少强制转换和类型错误。"),
        ("集合框架", "List、Set 与 Map 面向不同数据语义，应按顺序、唯一性和键值访问选择。"),
        ("Stream API", "Stream 以声明式流水线处理集合，需注意惰性求值与副作用。"),
        ("不可变对象", "不可变对象创建后状态不变，便于推理、缓存和并发共享。"),
        ("线程池", "线程池复用工作线程并限制并发资源，队列和拒绝策略必须显式配置。"),
        ("synchronized 与锁", "同步原语保护共享状态，但锁范围过大可能降低吞吐。"),
        ("JVM 内存区域", "堆、线程栈、方法区等区域承担不同运行时数据职责。"),
        ("垃圾回收", "垃圾回收追踪不可达对象并回收内存，暂停与吞吐需要平衡。"),
        ("类加载", "类加载经历加载、链接和初始化，并受类加载器层级影响。"),
        ("依赖注入", "依赖注入由容器提供对象依赖，降低构造与使用之间的耦合。"),
        ("Spring Bean 生命周期", "Bean 从实例化到初始化再到销毁，扩展点应放在合适阶段。"),
        ("Spring MVC", "Spring MVC 按请求映射、参数绑定、业务调用和响应转换处理 HTTP。"),
        ("REST 资源设计", "REST 接口应围绕资源与状态变化设计，并正确使用 HTTP 语义。"),
        ("参数校验", "入口校验应给出稳定错误码，业务不变量仍需在服务端再次保护。"),
        ("事务边界", "事务应包围必须原子完成的状态变化，并明确隔离和回滚条件。"),
        ("MyBatis Mapper", "Mapper 集中 SQL 与对象映射，Service 负责业务规则和事务编排。"),
        ("MyBatis Plus", "MyBatis Plus 提供通用 CRUD 与条件构造器，但复杂查询仍需清晰映射。"),
        ("乐观锁", "乐观锁用版本号检测并发覆盖，冲突时由调用方重新读取和决策。"),
        ("幂等接口", "幂等键和唯一约束共同防止重试造成重复写入。"),
        ("Spring Boot 配置", "配置应按环境分离，密钥由环境或密钥服务注入而不进入仓库。"),
    ]),
    "前端工程": ("MDN Web Docs", "Mozilla", "https://developer.mozilla.org/", [
        ("HTML 语义化", "语义元素表达内容结构，有助于可访问性、搜索和维护。"),
        ("CSS 盒模型", "元素尺寸由内容、内边距、边框和外边距共同决定。"),
        ("Flex 布局", "Flex 适合沿单一主轴分配空间与对齐项目。"),
        ("Grid 布局", "Grid 用行列网格控制二维页面布局。"),
        ("响应式设计", "响应式设计根据容器或视口调整布局，而非依赖单一设备尺寸。"),
        ("JavaScript 作用域", "词法作用域决定变量可见范围，let 与 const 具有块级作用域。"),
        ("闭包", "闭包让函数保留定义环境中的变量，常用于封装状态和回调。"),
        ("事件循环", "事件循环协调调用栈、任务和微任务，决定异步回调的执行顺序。"),
        ("Promise", "Promise 表达异步结果的最终完成或失败，并支持链式组合。"),
        ("async await", "async/await 以顺序形式组织 Promise，同时仍需显式处理失败。"),
        ("DOM", "DOM 把文档表示为可查询和修改的节点树。"),
        ("事件冒泡", "事件从目标向祖先传播，可用事件委托减少监听器数量。"),
        ("模块化", "ES 模块通过显式导入导出划分依赖和可见边界。"),
        ("组件化", "组件把结构、样式和行为组合成可复用的界面单元。"),
        ("单向数据流", "单向数据流让状态变化路径更可预测、更易调试。"),
        ("虚拟 DOM", "虚拟 DOM 用内存表示界面并计算更新，但性能仍取决于组件与数据设计。"),
        ("前端路由", "前端路由把 URL 与视图状态关联，并管理导航历史。"),
        ("表单校验", "客户端校验改善体验，服务端校验才是可信边界。"),
        ("Web 存储", "localStorage 与 sessionStorage 适合少量非敏感数据，不应存长期密钥。"),
        ("跨域资源共享", "CORS 由服务器声明允许的跨源请求条件，并非浏览器关闭同源策略。"),
        ("内容安全策略", "CSP 限制可执行脚本与可加载资源来源，降低注入攻击影响。"),
        ("可访问性", "键盘操作、语义、对比度和替代文本让更多用户可完成任务。"),
        ("性能指标", "LCP、INP 与 CLS 分别关注加载、交互响应和视觉稳定性。"),
        ("代码分割", "代码分割按页面或功能延迟加载资源，减少首屏下载与解析成本。"),
        ("前端测试", "单元、组件和端到端测试覆盖不同风险，重点应放在用户可观察行为。"),
    ]),
    "数据库": ("MySQL 8.4 Reference Manual", "Oracle", "https://dev.mysql.com/doc/refman/8.4/en/", [
        ("关系模型", "关系模型用表、行、列和约束表达数据及其一致性。"),
        ("主键", "主键唯一标识一行，应稳定、非空并避免承载可变业务含义。"),
        ("外键与逻辑关联", "外键提供数据库级引用约束，逻辑关联则把校验责任交给应用。"),
        ("唯一约束", "唯一约束是实现幂等和业务不重复的重要最后防线。"),
        ("范式", "规范化减少重复与更新异常，反规范化需用明确查询收益换取一致性成本。"),
        ("事务 ACID", "原子性、一致性、隔离性和持久性共同描述可靠事务。"),
        ("隔离级别", "隔离级别决定并发事务可见性，并影响异常现象与性能。"),
        ("MVCC", "MVCC 保存多版本可见性，使读写在许多场景下减少互相阻塞。"),
        ("行锁", "行锁保护具体记录，访问顺序不一致仍可能形成死锁。"),
        ("死锁", "死锁是事务循环等待资源，数据库会回滚其中一个事务解除僵局。"),
        ("索引", "索引用额外空间和写入成本换取更快的定位、排序或覆盖查询。"),
        ("B+ 树索引", "B+ 树保持有序并降低磁盘访问层级，适合范围和等值查询。"),
        ("联合索引", "联合索引按列顺序组织，查询能否利用前缀取决于条件形态。"),
        ("覆盖索引", "覆盖索引包含查询所需列，可减少回表访问。"),
        ("查询执行计划", "执行计划展示访问路径、连接顺序和估算行数，是优化起点。"),
        ("统计信息", "优化器依赖统计信息估算选择性，陈旧统计可能导致错误计划。"),
        ("分页", "深 OFFSET 分页成本会增长，稳定游标分页更适合大数据量。"),
        ("连接算法", "嵌套循环、哈希连接等算法的适用性取决于数据规模与索引。"),
        ("聚合查询", "聚合按分组计算统计值，筛选分组结果应使用 HAVING。"),
        ("NULL 语义", "NULL 表示未知或缺失，比较需使用 IS NULL 而不是等号。"),
        ("字符集与排序规则", "字符集决定编码，排序规则决定比较、排序及大小写敏感性。"),
        ("备份与恢复", "可恢复性必须通过定期备份、日志保留和恢复演练共同验证。"),
        ("读写分离", "读写分离扩展读取能力，但必须处理复制延迟与一致性要求。"),
        ("分库分表", "分片扩展容量也引入路由、跨分片事务和再平衡复杂度。"),
        ("数据库迁移", "迁移应可追踪、可重复判断且尽量向前兼容，避免无条件删表重建。"),
    ]),
    "云原生": ("Kubernetes Documentation", "Kubernetes", "https://kubernetes.io/docs/", [
        ("容器镜像", "容器镜像是分层只读文件系统与运行元数据的可分发打包。"),
        ("容器与虚拟机", "容器共享宿主内核，虚拟机通常包含独立客户操作系统。"),
        ("镜像标签与摘要", "标签可移动，内容摘要不可变；生产发布应记录可验证摘要。"),
        ("十二要素应用", "十二要素方法强调配置外置、无状态进程和可替换部署单元。"),
        ("Kubernetes Pod", "Pod 是 Kubernetes 最小调度单元，可包含共享网络和存储的容器。"),
        ("Deployment", "Deployment 声明无状态工作负载副本和滚动更新策略。"),
        ("Service", "Service 为一组动态 Pod 提供稳定的访问入口与服务发现。"),
        ("Ingress", "Ingress 按规则把外部 HTTP 流量路由到集群服务。"),
        ("ConfigMap", "ConfigMap 存放非敏感配置，使镜像与环境配置分离。"),
        ("Secret", "Secret 用于敏感配置，但仍需结合加密、最小权限和轮换。"),
        ("资源请求与限制", "请求影响调度，限制约束运行上限；错误配置会导致争抢或驱逐。"),
        ("健康检查", "存活、就绪和启动探针分别服务于重启、流量接入和慢启动。"),
        ("滚动发布", "滚动发布逐步替换实例，在容量与风险之间取得平衡。"),
        ("蓝绿发布", "蓝绿发布维护两套环境，通过切换流量实现快速回退。"),
        ("金丝雀发布", "金丝雀发布先让少量流量进入新版本，用观测结果控制扩大范围。"),
        ("水平自动扩缩容", "HPA 根据指标调整副本数，指标与冷却策略决定稳定性。"),
        ("服务发现", "服务发现让调用方通过稳定名称找到动态变化的实例。"),
        ("负载均衡", "负载均衡在多个实例间分配请求，并结合健康状态避开故障节点。"),
        ("可观测性", "日志、指标和追踪从不同角度解释系统状态，必须用统一上下文关联。"),
        ("分布式追踪", "追踪通过 trace 与 span 还原跨服务请求路径和耗时。"),
        ("熔断", "熔断在依赖持续失败时快速拒绝调用，给系统恢复窗口。"),
        ("限流", "限流保护容量边界，应明确作用范围、算法和超限响应。"),
        ("重试与退避", "重试只适合暂时性且可幂等的失败，并需退避与次数上限。"),
        ("基础设施即代码", "基础设施即代码让环境变更可审查、可复现并进入版本控制。"),
        ("最小权限", "工作负载、人员和自动化只获得完成任务所需的最少权限。"),
    ]),
    "网络安全": ("NIST Cybersecurity Framework", "NIST", "https://www.nist.gov/cyberframework", [
        ("机密性完整性可用性", "CIA 三要素分别关注防泄露、防篡改和可持续访问。"),
        ("身份认证与授权", "认证确认主体是谁，授权决定主体可以访问哪些资源。"),
        ("最小权限原则", "权限应限制到完成任务所需的最小集合，并定期复核。"),
        ("纵深防御", "纵深防御用多层独立控制降低单点失效造成的影响。"),
        ("威胁建模", "威胁建模识别资产、信任边界、攻击路径和优先缓解措施。"),
        ("输入校验", "输入校验应采用允许列表和结构约束，不能只依赖前端。"),
        ("SQL 注入", "参数化查询把数据与 SQL 结构分离，是防止注入的核心措施。"),
        ("跨站脚本", "XSS 利用未正确编码的不可信内容执行脚本，应按输出上下文编码。"),
        ("跨站请求伪造", "CSRF 借用已登录身份发起请求，需要令牌、SameSite 等控制。"),
        ("服务端请求伪造", "SSRF 诱导服务器访问攻击者指定地址，需限制协议、域名和网络范围。"),
        ("密码哈希", "密码应使用专用慢哈希和随机盐，不能用可逆加密或快速通用哈希。"),
        ("多因素认证", "多因素认证组合不同类别凭据，降低单一凭据泄露风险。"),
        ("会话管理", "会话令牌需高熵、限时、可撤销，并通过安全传输与存储保护。"),
        ("TLS", "TLS 为传输提供加密、完整性和服务器身份验证。"),
        ("密钥轮换", "密钥需要明确所有者、使用范围、有效期、轮换与吊销流程。"),
        ("日志脱敏", "日志不应记录令牌、验证码和完整个人敏感信息。"),
        ("依赖安全", "依赖清单、漏洞扫描、版本固定和升级验证共同降低供应链风险。"),
        ("安全响应头", "CSP、HSTS 等响应头为浏览器提供额外安全约束。"),
        ("文件上传安全", "上传需限制类型、大小和内容，隔离存储并避免以可执行方式提供。"),
        ("速率限制", "认证、搜索和高成本接口需要按主体与来源限制频率。"),
        ("审计日志", "审计日志记录关键管理与数据操作，并防止普通业务角色篡改。"),
        ("备份安全", "备份同样包含敏感数据，需要加密、访问控制和恢复演练。"),
        ("漏洞管理", "漏洞管理包含发现、评估、修复、验证和例外到期，而非只做扫描。"),
        ("事件响应", "事件响应应覆盖准备、识别、遏制、清除、恢复和复盘。"),
        ("隐私最小化", "只收集完成明确目的所需的数据，并设定保留期和删除路径。"),
    ]),
}

WORDS = {
    "primary": [
        ("apple", "/ˈæpəl/", "苹果", "I eat an apple every day.", "我每天吃一个苹果。"),
        ("book", "/bʊk/", "书", "This book is interesting.", "这本书很有趣。"),
        ("cat", "/kæt/", "猫", "The cat is under the chair.", "猫在椅子下面。"),
        ("dog", "/dɒɡ/", "狗", "The dog can run fast.", "这只狗跑得很快。"),
        ("family", "/ˈfæməli/", "家庭；家人", "My family has dinner together.", "我的家人一起吃晚饭。"),
        ("friend", "/frend/", "朋友", "She is my good friend.", "她是我的好朋友。"),
        ("happy", "/ˈhæpi/", "快乐的", "We are happy today.", "我们今天很开心。"),
        ("home", "/həʊm/", "家", "I go home after school.", "我放学后回家。"),
        ("learn", "/lɜːn/", "学习", "We learn English at school.", "我们在学校学习英语。"),
        ("morning", "/ˈmɔːnɪŋ/", "早晨", "Good morning, teacher.", "老师，早上好。"),
        ("music", "/ˈmjuːzɪk/", "音乐", "I like listening to music.", "我喜欢听音乐。"),
        ("school", "/skuːl/", "学校", "Our school is beautiful.", "我们的学校很漂亮。"),
        ("teacher", "/ˈtiːtʃə/", "老师", "The teacher helps us read.", "老师帮助我们阅读。"),
        ("water", "/ˈwɔːtə/", "水", "Please drink some water.", "请喝些水。"),
        ("window", "/ˈwɪndəʊ/", "窗户", "Open the window, please.", "请打开窗户。"),
        ("answer", "/ˈɑːnsə/", "回答；答案", "I know the answer.", "我知道答案。"),
        ("beautiful", "/ˈbjuːtɪfəl/", "美丽的", "The garden is beautiful.", "花园很美。"),
        ("color", "/ˈkʌlə/", "颜色", "What color is your bag?", "你的书包是什么颜色？"),
        ("different", "/ˈdɪfrənt/", "不同的", "The two pictures are different.", "这两幅图不同。"),
        ("easy", "/ˈiːzi/", "容易的", "This question is easy.", "这道题很容易。"),
        ("favorite", "/ˈfeɪvərɪt/", "最喜欢的", "Blue is my favorite color.", "蓝色是我最喜欢的颜色。"),
        ("important", "/ɪmˈpɔːtənt/", "重要的", "Breakfast is important.", "早餐很重要。"),
        ("question", "/ˈkwestʃən/", "问题", "May I ask a question?", "我可以问一个问题吗？"),
        ("remember", "/rɪˈmembə/", "记得", "Remember to bring your book.", "记得带上你的书。"),
        ("together", "/təˈɡeðə/", "一起", "Let us work together.", "让我们一起努力。"),
    ],
    "junior": [
        ("ability", "/əˈbɪləti/", "能力", "Reading improves our ability to think.", "阅读提升我们的思考能力。"),
        ("achieve", "/əˈtʃiːv/", "实现；达到", "Small steps help us achieve our goals.", "小步行动帮助我们实现目标。"),
        ("advice", "/ədˈvaɪs/", "建议", "Her advice helped me solve the problem.", "她的建议帮助我解决了问题。"),
        ("compare", "/kəmˈpeə/", "比较", "Compare the two methods carefully.", "仔细比较这两种方法。"),
        ("context", "/ˈkɒntekst/", "上下文；语境", "Context helps us understand the sentence.", "上下文帮助我们理解句子。"),
        ("create", "/kriˈeɪt/", "创造", "Students create a simple website.", "学生们创建一个简单的网站。"),
        ("decision", "/dɪˈsɪʒən/", "决定", "We made a decision after discussion.", "讨论后我们作出了决定。"),
        ("develop", "/dɪˈveləp/", "发展；开发", "Practice helps develop good habits.", "练习有助于养成好习惯。"),
        ("environment", "/ɪnˈvaɪrənmənt/", "环境", "We should protect the environment.", "我们应该保护环境。"),
        ("experience", "/ɪkˈspɪəriəns/", "经历；经验", "The project was a useful experience.", "这个项目是一次有用的经历。"),
        ("explain", "/ɪkˈspleɪn/", "解释", "Can you explain the rule again?", "你能再解释一下这条规则吗？"),
        ("improve", "/ɪmˈpruːv/", "改善；提高", "Daily review can improve memory.", "每日复习可以提高记忆。"),
        ("include", "/ɪnˈkluːd/", "包括", "The plan includes three activities.", "计划包括三项活动。"),
        ("information", "/ˌɪnfəˈmeɪʃən/", "信息", "Check the source before using the information.", "使用信息前先核查来源。"),
        ("method", "/ˈmeθəd/", "方法", "This method is easy to understand.", "这种方法容易理解。"),
        ("opinion", "/əˈpɪnjən/", "观点", "Please support your opinion with evidence.", "请用证据支持你的观点。"),
        ("organize", "/ˈɔːɡənaɪz/", "组织；整理", "I organize my notes every Friday.", "我每周五整理笔记。"),
        ("process", "/ˈprəʊses/", "过程", "Learning is a gradual process.", "学习是一个渐进的过程。"),
        ("result", "/rɪˈzʌlt/", "结果", "We checked the result twice.", "我们检查了两次结果。"),
        ("solve", "/sɒlv/", "解决", "The team worked together to solve it.", "团队一起解决了它。"),
        ("source", "/sɔːs/", "来源", "The article gives a clear source.", "这篇文章给出了清晰来源。"),
        ("suggest", "/səˈdʒest/", "建议", "I suggest testing the idea first.", "我建议先测试这个想法。"),
        ("technology", "/tekˈnɒlədʒi/", "技术", "Technology changes the way we learn.", "技术改变了我们的学习方式。"),
        ("understand", "/ˌʌndəˈstænd/", "理解", "Examples help us understand new ideas.", "例子帮助我们理解新概念。"),
        ("value", "/ˈvæljuː/", "价值；重视", "Good data has long-term value.", "优质数据具有长期价值。"),
    ],
    "senior": [
        ("accurate", "/ˈækjərət/", "准确的", "Accurate records support better decisions.", "准确记录有助于更好的决策。"),
        ("analyze", "/ˈænəlaɪz/", "分析", "We analyze the data before drawing a conclusion.", "我们在得出结论前分析数据。"),
        ("assumption", "/əˈsʌmpʃən/", "假设", "The experiment tests a key assumption.", "实验检验了一个关键假设。"),
        ("available", "/əˈveɪləbəl/", "可获得的；可用的", "The service is available at any time.", "该服务随时可用。"),
        ("complex", "/ˈkɒmpleks/", "复杂的", "A diagram can clarify a complex process.", "图示可以说明复杂过程。"),
        ("conclusion", "/kənˈkluːʒən/", "结论", "The evidence supports the conclusion.", "证据支持这一结论。"),
        ("consistent", "/kənˈsɪstənt/", "一致的", "The results are consistent across tests.", "各次测试结果一致。"),
        ("efficient", "/ɪˈfɪʃənt/", "高效的", "The new process is more efficient.", "新流程更加高效。"),
        ("evaluate", "/ɪˈvæljueɪt/", "评估", "We evaluate both benefits and risks.", "我们同时评估收益和风险。"),
        ("evidence", "/ˈevɪdəns/", "证据", "Reliable evidence is more important than confidence.", "可靠证据比自信更重要。"),
        ("factor", "/ˈfæktə/", "因素", "Cost is only one factor in the decision.", "成本只是决策中的一个因素。"),
        ("generate", "/ˈdʒenəreɪt/", "生成", "The model can generate a short summary.", "模型可以生成简短摘要。"),
        ("identify", "/aɪˈdentɪfaɪ/", "识别", "Logs help identify the cause of failure.", "日志有助于识别失败原因。"),
        ("maintain", "/meɪnˈteɪn/", "维护；保持", "Clear tests help maintain software quality.", "清晰测试有助于保持软件质量。"),
        ("objective", "/əbˈdʒektɪv/", "目标；客观的", "The objective is clear and measurable.", "目标清晰且可衡量。"),
        ("potential", "/pəˈtenʃəl/", "潜在的", "We discussed the potential risks.", "我们讨论了潜在风险。"),
        ("predict", "/prɪˈdɪkt/", "预测", "The model predicts demand from past data.", "模型根据历史数据预测需求。"),
        ("principle", "/ˈprɪnsəpəl/", "原则", "The design follows the principle of least privilege.", "该设计遵循最小权限原则。"),
        ("relevant", "/ˈreləvənt/", "相关的", "Only relevant evidence should enter the summary.", "只有相关证据应进入摘要。"),
        ("reliable", "/rɪˈlaɪəbəl/", "可靠的", "A reliable system handles failure safely.", "可靠系统能够安全处理失败。"),
        ("requirement", "/rɪˈkwaɪəmənt/", "要求；需求", "Each requirement needs a testable outcome.", "每项需求都需要可测试的结果。"),
        ("significant", "/sɪɡˈnɪfɪkənt/", "显著的；重要的", "The update produced a significant improvement.", "此次更新带来了显著提升。"),
        ("strategy", "/ˈstrætədʒi/", "策略", "A good strategy includes a fallback plan.", "好策略包含回退方案。"),
        ("structure", "/ˈstrʌktʃə/", "结构", "A clear structure makes the document easier to read.", "清晰结构使文档更易阅读。"),
        ("verify", "/ˈverɪfaɪ/", "验证", "Always verify important information with its source.", "重要信息应始终通过来源验证。"),
    ],
}


def ident(kind: str, *parts: str) -> str:
    return sha256(("letmindwander:v3.2:" + kind + ":" + ":".join(parts)).encode()).hexdigest()[:32]


def digest(*parts: str) -> str:
    return sha256("\n".join(parts).encode()).hexdigest()


def q(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def emit_insert(table, columns, values, updates):
    return "INSERT INTO `{}` ({}) VALUES ({}) ON DUPLICATE KEY UPDATE {};".format(
        table, ",".join("`%s`" % c for c in columns), ",".join(values),
        ",".join("`{0}`=VALUES(`{0}`)".format(c) for c in updates),
    )


def generate():
    lines = [
        "-- Generated by 05_code/scripts/generate_learning_seed.py",
        "-- MySQL 5.7.25; idempotent; no DROP TABLE and no table rebuild.",
        "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;",
        "SET SESSION time_zone = '+00:00';",
        "SET @has_stage := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_content' AND column_name='stage');",
        "SET @ddl := IF(@has_stage=0, 'ALTER TABLE `learning_content` ADD COLUMN `stage` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''primary/junior/senior for word''', 'DO 1');",
        "PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;",
        "SET @has_stage_idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='learning_content' AND index_name='idx_stage');",
        "SET @ddl := IF(@has_stage_idx=0, 'ALTER TABLE `learning_content` ADD KEY `idx_stage` (`content_type`,`stage`,`state`,`published_at`)', 'DO 1');",
        "PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;",
        "START TRANSACTION;",
        "",
    ]
    topic_ids = {}
    for topic, (source_name, author, url, cards) in TOPICS.items():
        assert len(cards) >= 25, topic
        source_id, topic_id = ident("source", topic), ident("topic", topic)
        topic_ids[topic] = topic_id
        lines.append(emit_insert("content_source", ["id", "name", "source_type", "url", "license_note", "enabled"],
            [q(source_id), q(source_name), q("manual"), q(url), q("原创结构化摘要；原文版权归来源方，链接用于延伸阅读"), "1"], ["name", "url", "license_note", "enabled"]))
        lines.append(emit_insert("learning_topic", ["id", "scope_key", "owner_id", "name", "normalized_name", "state"],
            [q(topic_id), q("system"), "NULL", q(topic), q(topic.lower()), q("active")], ["name", "state"]))
        for n, (title, summary) in enumerate(cards, 1):
            content_id, version_id = ident("tech", topic, title), ident("tech-version", topic, title)
            body = summary + " 学习时应同时确认它解决的问题、适用边界以及常见误用，并通过小例子验证理解。"
            lines.append(emit_insert("learning_content", ["id", "content_type", "source_id", "dedup_hash", "state", "current_version_id", "published_version_id", "published_at", "row_version"],
                [q(content_id), q("tech"), q(source_id), "UNHEX(%s)" % q(digest("tech", topic, title)), q("published"), q(version_id), q(version_id), "NOW(3)", "1"], ["state", "current_version_id", "published_version_id"]))
            lines.append(emit_insert("content_version", ["id", "content_id", "version_no", "title", "summary", "body", "difficulty", "estimated_seconds", "origin_url", "origin_author", "origin_published_at", "license_snapshot", "body_hash", "review_status", "reviewed_at", "created_by"],
                [q(version_id), q(content_id), "1", q(title), q(summary), q(body), q("intro" if n <= 17 else "advanced"), "180", q(url), q(author), "NULL", q("原创结构化摘要；延伸阅读请访问来源链接"), "UNHEX(%s)" % q(digest(body)), q("approved"), "NOW(3)", q(ADMIN_ID)], ["title", "summary", "body", "difficulty", "estimated_seconds", "origin_url", "license_snapshot", "review_status"]))
            relation_id = ident("content-topic", version_id, topic_id)
            lines.append(emit_insert("content_topic", ["id", "content_version_id", "topic_id"], [q(relation_id), q(version_id), q(topic_id)], ["topic_id"]))
        lines.append("")

    word_source = ident("source", "english-curriculum")
    lines.append(emit_insert("content_source", ["id", "name", "source_type", "url", "license_note", "enabled"],
        [q(word_source), q("英语课程基础词汇整理"), q("manual"), q("https://www.gov.cn/zhengce/zhengceku/2022-04/21/content_5686535.htm"), q("依据公开课程标准选取基础词汇，释义与例句为原创整理"), "1"], ["name", "url", "license_note", "enabled"]))
    for stage, words in WORDS.items():
        assert len(words) >= 25, stage
        for n, (word, phonetic, meaning, example, translation) in enumerate(words, 1):
            content_id, version_id = ident("word", word), ident("word-version", word)
            sense_id, example_id = ident("sense", word), ident("example", word)
            lines.append(emit_insert("learning_content", ["id", "content_type", "source_id", "dedup_hash", "word_key_hash", "stage", "state", "current_version_id", "published_version_id", "published_at", "row_version"],
                [q(content_id), q("word"), q(word_source), "UNHEX(%s)" % q(digest("word", word)), "UNHEX(%s)" % q(digest(word.lower())), q(stage), q("published"), q(version_id), q(version_id), "NOW(3)", "1"], ["stage", "state", "current_version_id", "published_version_id"]))
            lines.append(emit_insert("content_version", ["id", "content_id", "version_no", "title", "summary", "body", "difficulty", "estimated_seconds", "word_term", "phonetic", "meaning", "example_text", "example_translation", "origin_url", "origin_author", "license_snapshot", "body_hash", "review_status", "reviewed_at", "created_by"],
                [q(version_id), q(content_id), "1", q(word), q(meaning), q(meaning), q("intro" if n <= 18 else "advanced"), "30", q(word), q(phonetic), q(meaning), q(example), q(translation), q("https://www.gov.cn/zhengce/zhengceku/2022-04/21/content_5686535.htm"), q("课程词汇整理"), q("释义与例句为原创整理，仅作学习用途"), "UNHEX(%s)" % q(digest(word, meaning, example)), q("approved"), "NOW(3)", q(ADMIN_ID)], ["title", "summary", "body", "difficulty", "word_term", "phonetic", "meaning", "example_text", "example_translation", "review_status"]))
            lines.append(emit_insert("word_sense", ["id", "content_version_id", "part_of_speech", "meaning", "sort_no"], [q(sense_id), q(version_id), q("other"), q(meaning), "1"], ["meaning"]))
            lines.append(emit_insert("word_example", ["id", "sense_id", "sentence", "translation", "sort_no"], [q(example_id), q(sense_id), q(example), q(translation), "1"], ["sentence", "translation"]))
        lines.append("")
    lines += [
        "COMMIT;",
        "",
        "-- Coverage checks: every technical topic and English stage must be at least 25.",
        "SELECT t.name AS knowledge_type, COUNT(DISTINCT lc.id) AS item_count FROM learning_topic t JOIN content_topic ct ON ct.topic_id=t.id JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.published_version_id=cv.id WHERE t.id IN (%s) AND lc.state='published' GROUP BY t.id,t.name ORDER BY t.name;" % ",".join(q(v) for v in topic_ids.values()),
        "SELECT stage,COUNT(*) AS item_count FROM learning_content WHERE content_type='word' AND state='published' AND stage IN ('primary','junior','senior') GROUP BY stage ORDER BY stage;",
        "",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    OUTPUT.write_text(generate(), encoding="utf-8")
    print(OUTPUT)
