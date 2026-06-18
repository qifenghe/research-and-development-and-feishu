const state = {
  currentUser: "研发总监 · 赵新武",
  product: "500g香卤大肠头",
  sampleVersion: "A1",
  pricingVersion: "A1-核价V1",
};

const sidebarItems = [
  ["dashboard", "工作台", "#246BFE"],
  ["demand-module", "样品需求", "#F59E0B"],
  ["rnd-module", "研发任务", "#16A34A"],
  ["shipment-pricing-module", "寄样核价", "#6D5BD0"],
  ["settings-module", "系统设置", "#64748B"],
];

const sampleRows = [
  ["500g香卤大肠头", "A0", "黄丽金", "待内部测试", "06-20", "internal-test"],
  ["黑椒鸡柳料理包", "A1", "李工", "待寄样反馈", "06-21", "shipment-list"],
  ["调理鸡排", "A2", "王工", "待生成核价", "06-24", "pricing-list"],
  ["腊肠新口味", "A0", "待分发", "任务池", "06-25", "task-pool"],
];

const sampleRecords = [
  { product: "500g香卤大肠头", version: "A0", customer: "客户A", owner: "黄丽金", status: "待内部测试", date: "2026-06-20", target: "internal-test" },
  { product: "黑椒鸡柳料理包", version: "A1", customer: "客户C", owner: "李工", status: "待寄样反馈", date: "2026-06-21", target: "shipment-list" },
  { product: "调理鸡排", version: "A2", customer: "客户D", owner: "王工", status: "待生成核价", date: "2026-06-24", target: "pricing-list" },
  { product: "腊肠新口味", version: "A0", customer: "客户B", owner: "待分发", status: "任务池", date: "2026-06-25", target: "task-pool" },
  { product: "麻辣牛肉料理包", version: "A1", customer: "客户E", owner: "陈工", status: "待财务核价", date: "2026-06-18", target: "finance" },
  { product: "蒜香鸡翅中", version: "A0", customer: "客户F", owner: "黄丽金", status: "实验单草稿", date: "2026-06-17", target: "experiment" },
];

const dataModelGroups = [
  {
    title: "1. 主业务模型",
    scope: "样品需求、项目主档和版本主线",
    status: "优先确认",
    models: [
      ["SampleRequest", "样品需求单", "研发内勤创建，研发总监审核，是流程起点"],
      ["SampleProject", "样品项目/样品主档", "承载版本、任务、实验、测试、寄样、核价和归档"],
      ["SampleVersion", "样品版本", "A0、A1、A2等，所有过程记录必须挂具体版本"],
    ],
  },
  {
    title: "2. 研发任务模型",
    scope: "任务池、分发、现场实验和实验单历史",
    status: "待确认",
    models: [
      ["RndTask", "研发任务", "从任务池分发给研发人员，记录负责人、期限和接受时间"],
      ["ExperimentForm", "打样实验单", "支持草稿、提交、锁定和历史版本"],
      ["ExperimentMaterial", "实验原辅料/包材明细", "记录原料、辅料、包材、用量、利用率和损耗"],
      ["ExperimentProcess", "实验工序记录", "记录清洗、保水、卤制、冷却、包装等过程数据"],
      ["ExperimentAttachment", "实验附件", "现场照片、称重照片、成品照片和文件附件"],
    ],
  },
  {
    title: "3. 内部测试模型",
    scope: "测试任务、测试记录和锁版触发",
    status: "待确认",
    models: [
      ["TestAssignment", "内部测试任务", "记录测试人员、测试项目和通知状态"],
      ["TestRecord", "内部测试记录", "记录口味、口感、出水、复热、外观和测试结论"],
    ],
  },
  {
    title: "4. 寄样与核价模型",
    scope: "寄样反馈、核价文件和财务通知",
    status: "待确认",
    models: [
      ["ShipmentRecord", "寄样记录", "记录寄样版本、数量、快递、收件信息和寄样时间"],
      ["CustomerFeedback", "客户反馈", "记录通过、不通过、继续打样或停止"],
      ["PricingFile", "核价文件", "记录核价Excel版本，关联样品版本和模板"],
      ["FinanceNotification", "财务核价通知", "记录接收人、通知时间、飞书消息和财务核价状态"],
    ],
  },
  {
    title: "5. 文件与审计模型",
    scope: "归档文件、审计日志和搜索索引",
    status: "待确认",
    models: [
      ["ArchiveFile", "归档文件", "统一管理需求单、实验单、测试记录、照片、寄样和核价文件"],
      ["AuditLog", "审计日志", "记录创建、保存、提交、退回、锁定、生成文件、通知财务"],
      ["SearchIndex", "样品检索索引", "用于按名称、客户、负责人、状态、时间快速搜索"],
    ],
  },
  {
    title: "6. 配置中心模型",
    scope: "账号权限、表单、流程、通知、模板、字典和文件规则",
    status: "待确认",
    models: [
      ["UserAccount", "用户账号", "对接飞书用户，记录姓名、部门、角色和状态"],
      ["RolePermission", "角色权限", "配置菜单、按钮和数据范围权限"],
      ["FormFieldConfig", "表单字段配置", "配置需求、实验、测试、寄样、核价字段"],
      ["WorkflowConfig", "流程状态配置", "配置状态、按钮、流转规则和通知要求"],
      ["NotificationConfig", "飞书通知配置", "配置通知对象、消息卡片和催办规则"],
      ["TemplateConfig", "模板配置", "配置实验单、测试记录和核价Excel模板"],
      ["DictionaryItem", "基础字典", "产品类型、单位、物料类别、状态标签、客户标签"],
      ["FileRuleConfig", "文件归档规则", "文件夹命名、版本号、锁定只读和存储路径"],
    ],
  },
];

const views = [
  { id: "dashboard", title: "PC 总管理后台", subtitle: "研发总监查看全部样品节点、超期任务、待办和文件归档状态", render: renderDashboard },
  { id: "demand-module", title: "样品需求", subtitle: "研发内勤录需求，研发总监审核，审核通过后进入任务池", render: renderDemandModule },
  { id: "request-new", title: "研发内勤：录入样品需求", subtitle: "研发内勤从飞书进入，录入需要做的样品清单", render: renderRequestNew },
  { id: "request-review", title: "研发总监：需求审核", subtitle: "研发总监判断资料是否完整，通过后进入任务池", render: renderRequestReview },
  { id: "rnd-module", title: "研发任务", subtitle: "任务池、分发、研发接任务、实验单、内部测试和锁版都在这里处理", render: renderRndModule },
  { id: "task-pool", title: "PC端：研发任务池", subtitle: "所有审核通过但尚未分配的任务先进入任务池", render: renderTaskPool },
  { id: "task-assign", title: "PC端：任务分发", subtitle: "研发总监分发给具体研发人员，飞书自动通知到人", render: renderTaskAssign },
  { id: "mobile-home", title: "手机/平板端：研发人员接任务", subtitle: "研发人员在自己的飞书账号内接受任务", render: renderMobileHome },
  { id: "experiment", title: "手机/平板端：打样实验单", subtitle: "现场录入实验数据，先保存草稿，再通知内部测试", render: renderExperiment },
  { id: "experiment-history", title: "实验单历史版本", subtitle: "按样品版本查阅已锁定和草稿实验单，保留每次打样调整记录", render: renderExperimentHistory },
  { id: "test-config", title: "PC端：内部测试人员配置", subtitle: "为样品版本选择测试人员、测试项目和通知方式", render: renderTestConfig },
  { id: "internal-test", title: "手机/平板端：内部测试确认", subtitle: "测试通过后提交实验单并锁定版本，历史版本自动归档", render: renderInternalTest },
  { id: "shipment-pricing-module", title: "寄样核价", subtitle: "寄样反馈、客户意见、核价文件生成和通知财务集中处理", render: renderShipmentPricingModule },
  { id: "shipment-list", title: "寄样反馈列表", subtitle: "多个样品同时等待反馈，先列表排队，再进入详情处理", render: renderShipmentList },
  { id: "shipment-detail", title: "寄样反馈详情", subtitle: "登记寄样信息、客户反馈，通过后生成核价文件", render: renderShipmentDetail },
  { id: "pricing-list", title: "核价文件列表", subtitle: "可从样品完成后单独生成核价，也可从客户通过后生成核价", render: renderPricingList },
  { id: "pricing-detail", title: "核价文件详情", subtitle: "按Excel模板生成版本化核价文件，并通知财务", render: renderPricingDetail },
  { id: "finance", title: "通知财务核价", subtitle: "核价文件生成后推送财务，流程到这里结束", render: renderFinance },
  { id: "archive", title: "样品文件归档", subtitle: "按产品、版本、阶段保存需求、实验单、测试、寄样、核价文件", render: renderArchive },
  { id: "settings-module", title: "配置中心", subtitle: "把流程里可变化的规则解耦出来，统一维护、统一被流程调用", render: renderSettingsModule },
  { id: "data-models", title: "数据模型确认清单", subtitle: "按业务关系逐个确认数据模型，再展开字段设计", render: renderDataModels },
  { id: "config", title: "人员配置与权限", subtitle: "维护研发内勤、研发总监、研发、测试、财务、管理员权限", render: renderConfig },
  { id: "form-config", title: "表单字段配置", subtitle: "维护需求单、实验单、测试单、寄样反馈、核价文件字段", render: renderFormConfig },
  { id: "workflow-config", title: "流程状态配置", subtitle: "维护状态、节点、流转按钮和是否需要审批/通知", render: renderWorkflowConfig },
  { id: "notification-config", title: "飞书通知配置", subtitle: "维护每个节点通知谁、发什么卡片、是否可催办", render: renderNotificationConfig },
  { id: "template-config", title: "模板配置", subtitle: "维护实验单模板、测试模板和核价Excel模板", render: renderTemplateConfig },
  { id: "dictionary-config", title: "基础字典配置", subtitle: "维护产品类型、单位、客户标签、状态标签和物料类别", render: renderDictionaryConfig },
  { id: "file-management", title: "文件管理与存档规则", subtitle: "配置文件夹、版本号、模板、飞书云盘和MinIO/NAS路径", render: renderFileManagement },
  { id: "flowchart", title: "研发样品全流程图", subtitle: "按飞书自建应用的角色、状态、通知和归档链路展示", render: renderFlowchart },
  { id: "stopped", title: "停止/废弃项目池", subtitle: "复打样无效或项目取消时，记录原因并保留历史资料", render: renderStopped },
];

const routeAliases = {
  demand: "demand-module",
  rnd: "rnd-module",
  shipment: "shipment-list",
  pricing: "pricing-list",
  archiveCenter: "archive",
  settings: "settings-module",
  test: "internal-test",
};

const moduleMap = {
  "request-new": "demand-module",
  "request-review": "demand-module",
  "task-pool": "rnd-module",
  "task-assign": "rnd-module",
  "mobile-home": "rnd-module",
  "experiment": "rnd-module",
  "experiment-history": "rnd-module",
  "test-config": "rnd-module",
  "internal-test": "rnd-module",
  "shipment-list": "shipment-pricing-module",
  "shipment-detail": "shipment-pricing-module",
  "pricing-list": "shipment-pricing-module",
  "pricing-detail": "shipment-pricing-module",
  "finance": "shipment-pricing-module",
  "archive": "shipment-pricing-module",
  "stopped": "rnd-module",
  "config": "settings-module",
  "data-models": "settings-module",
  "form-config": "settings-module",
  "workflow-config": "settings-module",
  "notification-config": "settings-module",
  "template-config": "settings-module",
  "dictionary-config": "settings-module",
  "file-management": "settings-module",
  "flowchart": "dashboard",
};

function routeId() {
  const raw = location.hash.replace("#", "") || "dashboard";
  return routeAliases[raw] || raw;
}

function go(id) {
  location.hash = id;
}

function viewById(id) {
  return views.find((view) => view.id === id) || views[0];
}

function appShell(view) {
  const activeModule = moduleMap[view.id] || view.id;
  const nav = sidebarItems.map(([id, label, color]) => `
    <a class="side-link ${id === activeModule ? "active" : ""}" href="#${id}">
      <span class="dot" style="background:${color}"></span>${label}
    </a>
  `).join("");

  const chips = [
    ["dashboard", "PC后台"],
    ["mobile-home", "手机入口"],
    ["flowchart", "流程图"],
    ["settings-module", "系统设置"],
  ].map(([id, label]) => `<a class="nav-chip ${id === view.id || id === activeModule ? "active" : ""}" href="#${id}">${label}</a>`).join("");

  return `
    <div class="app-shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark">研</div>
          <div>
            <h1>飞书自建应用研发样品管理系统</h1>
            <p>第五版交互原型 · 样品通过后止于核价文件与财务通知</p>
          </div>
        </div>
        <div class="top-actions">
          ${chips}
          <button class="secondary" data-route="request-new">从研发内勤录需求开始</button>
        </div>
      </header>
      <div class="workspace">
        <aside class="sidebar">
          <div class="sidebar-title">流程页面</div>
          ${nav}
        </aside>
        <main class="main">
          <section class="hero">
            <div class="hero-row">
              <div>
                <h2>${view.title}</h2>
                <p>${view.subtitle}</p>
              </div>
              <span class="badge">当前用户：${state.currentUser}</span>
            </div>
          </section>
          <section class="page">${view.render()}</section>
        </main>
      </div>
      <div id="toast" class="toast"></div>
    </div>
  `;
}

function renderDashboard() {
  return `
    <div class="card">
      <div class="grid cols-4">
        ${stat("待总监审核", "6", "var(--orange)")}
        ${stat("任务池待分发", "9", "var(--purple)")}
        ${stat("待内部测试", "5", "var(--teal)")}
        ${stat("待财务核价", "3", "var(--red)")}
      </div>
      ${renderSearchPanel()}
      <div class="section-title"><h3>业务模块入口</h3><a class="button secondary" href="#flowchart">查看流程图</a></div>
      <div class="module-grid">
        ${moduleCard("样品需求", "研发内勤录入，总监审核后进入任务池", "demand-module", "orange")}
        ${moduleCard("研发任务", "任务池、分发、实验、测试、锁版", "rnd-module", "green")}
        ${moduleCard("寄样核价", "寄样反馈、核价文件、通知财务核价", "shipment-pricing-module", "purple")}
        ${moduleCard("系统设置", "人员权限、文件规则、模板规则", "settings-module", "gray")}
      </div>
      <div class="section-title"><h3>样品任务总览</h3></div>
      ${taskTable()}
      <div class="actions">
        <button data-route="request-new">研发内勤录入需求</button>
        <button class="green" data-route="rnd-module">进入研发任务</button>
        <button class="orange" data-route="shipment-pricing-module">进入寄样核价</button>
        <button class="secondary" data-route="settings-module">系统设置</button>
      </div>
    </div>
  `;
}

function renderSearchPanel() {
  return `
    <div class="search-panel">
      <div class="section-title"><h3>样品快速搜索</h3><span class="badge">按名称 / 时间查找</span></div>
      <div class="search-grid">
        <div class="field">
          <label>名称关键词</label>
          <input id="search-name" placeholder="产品名、客户、负责人、状态" value="" />
        </div>
        <div class="field">
          <label>开始日期</label>
          <input id="search-start" type="date" value="2026-06-17" />
        </div>
        <div class="field">
          <label>结束日期</label>
          <input id="search-end" type="date" value="2026-06-25" />
        </div>
        <div class="search-actions">
          <button data-search-action="search">搜索</button>
          <button class="secondary" data-search-action="reset">重置</button>
        </div>
      </div>
      <div id="search-results" class="search-results">
        ${searchResultsTable(sampleRecords)}
      </div>
    </div>
  `;
}

function searchResultsTable(records) {
  if (!records.length) {
    return `<div class="empty-state">没有找到匹配样品，可以放宽名称或时间范围。</div>`;
  }

  return `
    <div class="table compact-table">
      <div class="row header"><div>产品</div><div>版本</div><div>客户</div><div>状态</div><div>日期</div><div>操作</div></div>
      ${records.map((item) => `
        <div class="row">
          <strong>${item.product}</strong>
          <div>${item.version}</div>
          <div>${item.customer} / ${item.owner}</div>
          <div>${statusBadge(item.status)}</div>
          <div>${formatShortDate(item.date)}</div>
          <a class="button secondary" href="#${item.target}">查看</a>
        </div>
      `).join("")}
    </div>
  `;
}

function filterSampleRecords() {
  const keyword = document.querySelector("#search-name")?.value.trim().toLowerCase() || "";
  const start = document.querySelector("#search-start")?.value || "";
  const end = document.querySelector("#search-end")?.value || "";

  const records = sampleRecords.filter((item) => {
    const haystack = `${item.product} ${item.customer} ${item.owner} ${item.status} ${item.version}`.toLowerCase();
    const matchName = !keyword || haystack.includes(keyword);
    const matchStart = !start || item.date >= start;
    const matchEnd = !end || item.date <= end;
    return matchName && matchStart && matchEnd;
  });

  const target = document.querySelector("#search-results");
  if (target) {
    target.innerHTML = searchResultsTable(records);
  }
}

function resetSampleSearch() {
  const name = document.querySelector("#search-name");
  const start = document.querySelector("#search-start");
  const end = document.querySelector("#search-end");
  if (name) name.value = "";
  if (start) start.value = "2026-06-17";
  if (end) end.value = "2026-06-25";
  filterSampleRecords();
}

function renderDemandModule() {
  return modulePage("样品需求模块", "研发内勤和研发总监在这里完成需求入口控制。", [
    ["1", "研发内勤录入需求", "把业务员/客户提出的样品清单录入系统", "request-new", "blue"],
    ["2", "研发总监审核", "检查信息是否完整，可退回补充或通过", "request-review", "orange"],
    ["3", "进入任务池", "审核通过后进入研发任务池等待分发", "task-pool", "purple"],
  ], [
    ["待审核需求", "6", "var(--orange)"],
    ["退回补充", "2", "var(--red)"],
    ["今日新增", "4", "var(--blue)"],
  ]);
}

function renderRndModule() {
  return modulePage("研发任务模块", "研发总监、研发人员、内部测试人员围绕打样和锁版协作。", [
    ["1", "任务池", "审核通过的需求先进入待分发池", "task-pool", "purple"],
    ["2", "任务分发", "研发总监指定研发人员，飞书自动通知", "task-assign", "purple"],
    ["3", "研发接任务", "研发人员在手机/平板端接受任务", "mobile-home", "green"],
    ["4", "实验单保存", "现场录入配方、工序、重量、照片，先保存不锁版", "experiment", "green"],
    ["5", "历史版本", "查阅A0/A1/A2实验单和每版调整记录", "experiment-history", "blue"],
    ["6", "配置内部测试", "选择测试人员、测试项目和通知方式", "test-config", "teal"],
    ["7", "测试通过锁版", "测试通过后提交实验单并锁定历史版本", "internal-test", "teal"],
  ], [
    ["任务池待分发", "9", "var(--purple)"],
    ["打样中", "8", "var(--green)"],
    ["待内部测试", "5", "var(--teal)"],
  ]);
}

function renderShipmentPricingModule() {
  return modulePage("寄样核价模块", "样品完成后在这里处理寄样反馈、核价文件生成、财务核价通知和文件留存。", [
    ["1", "寄样反馈列表", "多个样品排队，先列表定位样品", "shipment-list", "orange"],
    ["2", "寄样反馈详情", "登记快递、客户反馈、通过/复打样/停止", "shipment-detail", "orange"],
    ["3", "核价文件列表", "样品完成后可单独生成核价文件", "pricing-list", "blue"],
    ["4", "核价文件详情", "按Excel模板生成版本化核价文件", "pricing-detail", "blue"],
    ["5", "通知财务核价", "提交核价文件，财务收到飞书待办", "finance", "red"],
    ["6", "核价资料归档", "留存实验、测试、寄样和核价文件", "archive", "gray"],
  ], [
    ["待寄样反馈", "7", "var(--orange)"],
    ["待生成核价", "4", "var(--blue)"],
    ["待财务报价", "3", "var(--red)"],
  ]);
}

function renderSettingsModule() {
  const configs = [
    ["数据模型", "25个模型、6组关系、逐组确认字段", "data-models", "green", "全系统"],
    ["人员与权限", "角色、账号、可见范围、按钮权限", "config", "gray", "全部模块"],
    ["表单字段", "需求单、实验单、测试单、寄样反馈、核价字段", "form-config", "blue", "录入/实验/核价"],
    ["流程状态", "节点状态、按钮、退回、停止、锁版规则", "workflow-config", "green", "研发任务"],
    ["测试规则", "测试人员、测试项目、评分项、通过条件", "test-config", "teal", "内部测试"],
    ["飞书通知", "通知对象、卡片按钮、催办、到期提醒", "notification-config", "orange", "任务/财务"],
    ["模板规则", "实验单模板、测试模板、核价Excel模板", "template-config", "purple", "文件生成"],
    ["文件归档", "文件夹、版本号、锁定只读、存储位置", "file-management", "blue", "归档留存"],
    ["基础字典", "产品类型、单位、客户标签、物料类别", "dictionary-config", "gray", "所有表单"],
  ];

  return `
    <div class="card">
      <div class="section-title"><h3>配置中心</h3><span class="badge">流程与配置解耦</span></div>
      <p class="module-intro">业务流程页面不直接写死人员、字段、模板、通知和归档规则，而是统一从配置中心读取。后续调整测试人员、字段名称、核价模板或通知对象时，只改配置，不改流程。</p>
      <div class="grid cols-4">
        ${stat("可配置项", "9类", "var(--blue)")}
        ${stat("影响模块", "5个", "var(--green)")}
        ${stat("通知节点", "7个", "var(--orange)")}
        ${stat("数据模型", "25个", "var(--purple)")}
      </div>
      <div class="section-title"><h3>配置项矩阵</h3></div>
      <div class="config-grid">
        ${configs.map(([title, desc, target, color, scope]) => configCard(title, desc, target, color, scope)).join("")}
      </div>
    </div>
  `;
}

function renderRequestNew() {
  return mobileLayout("新建样品需求", "研发内勤 · 飞书移动端/PC均可", `
    ${field("需求来源", "业务员李经理 / 客户A", true)}
    ${field("产品名称", state.product, true)}
    ${field("产品类型", "冷冻即热菜 / 酱卤肉制品", true)}
    ${field("规格要求", "500g/袋，20袋/箱", true)}
    ${field("打样数量", "6袋", true)}
    ${field("期望完成日期", "2026-06-25", true)}
    ${field("样品要求", "偏软糯，复热后不明显出水；需要记录关键原料利用率。", false, "textarea")}
    <div class="actions">
      <button class="secondary" data-toast="需求草稿已保存">保存草稿</button>
      <button data-route="request-review">提交研发总监审核</button>
    </div>
  `, `
    <div class="card">
      <h3>这个页面由研发内勤使用</h3>
      <p>研发内勤把需要做的样品清单录进系统，提交后研发总监收到飞书消息卡片。后续所有样品版本、实验单、测试、寄样、核价文件都从这条需求延伸。</p>
      <div class="actions"><button data-route="request-review">进入总监审核</button></div>
    </div>
  `);
}

function renderRequestReview() {
  return `
    <div class="card">
      <div class="section-title"><h3>待审核样品需求</h3><span class="badge orange">待总监审核</span></div>
      ${detailGrid([
        ["录入人", "研发内勤 · 刘敏"],
        ["产品名称", state.product],
        ["产品类型", "冷冻即热菜 / 酱卤肉制品"],
        ["客户", "客户A"],
        ["规格", "500g/袋"],
        ["期望日期", "2026-06-25"],
      ])}
      <div class="field" style="margin-top:14px">
        <label>研发总监审核意见</label>
        <textarea>资料完整，同意进入研发任务池。分发时需指定测试人员，重点测试复热出水和口感软糯度。</textarea>
      </div>
      <div class="actions">
        <button class="secondary" data-toast="已退回研发内勤补充资料">退回补充</button>
        <button data-route="task-pool">审核通过，进入任务池</button>
      </div>
    </div>
  `;
}

function renderTaskPool() {
  return `
    <div class="card">
      <div class="section-title"><h3>任务池</h3><span class="badge">审核通过待分发</span></div>
      <div class="table">
        <div class="row header"><div>样品需求</div><div>版本</div><div>来源</div><div>状态</div><div>期限</div><div>操作</div></div>
        ${[
          [state.product, "A0", "客户A", "待分发", "06-20", "task-assign"],
          ["腊肠新口味", "A0", "客户B", "待分发", "06-22", "task-assign"],
          ["黑椒鸡柳料理包", "A1", "客户C", "待转派", "06-23", "task-assign"],
        ].map(row).join("")}
      </div>
      <div class="actions">
        <button data-route="task-assign">选择任务并分发</button>
      </div>
    </div>
  `;
}

function renderTaskAssign() {
  return `
    <div class="card">
      <div class="section-title"><h3>研发任务分发</h3><span class="badge">由研发总监操作</span></div>
      <div class="form-grid">
        ${field("指派研发人员", "黄丽金", true)}
        ${field("任务期限", "2026-06-20", true)}
        ${field("样品版本", "A0 首轮打样", true)}
        ${field("飞书通知", "自动发送给黄丽金", true)}
      </div>
      <div class="field" style="margin-top:14px">
        <label>任务说明</label>
        <textarea>按500g/袋规格完成首轮打样，记录清洗、保水、卤制、出成、损耗、关键原料利用率。</textarea>
      </div>
      <div class="actions">
        <button class="secondary" data-toast="分发草稿已保存">保存分发草稿</button>
        <button data-route="mobile-home">分发任务并飞书通知研发</button>
      </div>
    </div>
  `;
}

function renderMobileHome() {
  return mobileLayout("我的研发任务", "飞书工作台 · 黄丽金 / 研发", `
    <div class="actions" style="margin:12px 0 6px">
      <span class="badge orange">待接受 1</span>
      <span class="badge green">打样中 2</span>
    </div>
    ${phoneCard("新研发任务", "500g香卤大肠头 A0", "研发总监已分发，等待接受", "待接受", "orange", "experiment")}
    ${phoneCard("继续填写实验单", "调理鸡排 A2", "草稿已保存，待补充工序照片", "草稿", "green", "experiment")}
    <h3 style="margin-top:20px">快捷入口</h3>
    <div class="quick-grid">
      <a class="quick" href="#experiment"><span class="dot" style="background:var(--green)"></span>实验单</a>
      <a class="quick" href="#internal-test"><span class="dot" style="background:var(--teal)"></span>内部测试</a>
      <a class="quick" href="#shipment-list"><span class="dot" style="background:var(--orange)"></span>寄样反馈</a>
      <a class="quick" href="#archive"><span class="dot" style="background:var(--blue)"></span>文件归档</a>
    </div>
  `, `
    <div class="card">
      <h3>研发人员在自己的账号里处理</h3>
      <p>飞书消息卡片会直接跳到这条任务。接受后进入实验单，现场用手机/平板录入数据和照片。</p>
      <div class="actions"><button class="green" data-route="experiment">接受任务并开始实验</button></div>
    </div>
  `, true);
}

function renderExperiment() {
  return mobileLayout("打样实验单", `任务：${state.product} A0 · 研发：黄丽金`, `
    <div class="actions" style="margin:12px 0 6px">
      <span class="badge green">打样中</span>
      <span class="badge">未锁定</span>
    </div>
    ${field("产品名称", state.product, true)}
    ${field("产品规格", "500g/袋，20袋/箱", true)}
    ${field("工序节点", "清洗 / 保水 / 卤制 / 冷却 / 包装", true)}
    ${field("研发参考出成", "89 kg", true)}
    <div class="phone-card">
      <h4>原辅料明细</h4>
      <p>冻猪大肠头 100kg · 利用率95%</p>
      <p>去腥粉 0.03kg · 香辛料包 1.2kg · 包材500g袋178个</p>
    </div>
    <div class="actions">
      <button class="secondary" data-toast="实验单草稿已保存，仍可修改">保存实验单</button>
      <button class="secondary" data-toast="照片上传入口模拟">上传现场照片</button>
      <button class="secondary" data-route="experiment-history">查看历史版本</button>
      <button data-route="test-config">通知内部测试人员</button>
    </div>
  `, `
    <div class="card">
      <h3>锁版时机调整</h3>
      <p>研发人员填写完实验单后先保存，不立即锁定。通知内部测试人员完成测试，测试通过后再由系统提交实验单并锁定版本。</p>
      <div class="actions">
        <button class="secondary" data-route="experiment-history">查看实验单历史版本</button>
        <button data-route="test-config">配置测试人员</button>
      </div>
    </div>
  `);
}

function renderExperimentHistory() {
  const versions = [
    ["A0", "已锁定", "2026-06-18 10:42", "黄丽金", "首轮小试，口感偏硬，出水轻微", "archive"],
    ["A1", "已锁定", "2026-06-19 15:18", "黄丽金", "调整卤制时间和保水比例，内部测试通过", "archive"],
    ["A2", "草稿", "2026-06-20 09:30", "黄丽金", "客户建议口味微调，待测试", "experiment"],
  ];

  return `
    <div class="grid cols-2">
      <div class="card">
        <div class="section-title"><h3>${state.product} · 实验单历史版本</h3><span class="badge">可追溯</span></div>
        <div class="table version-table">
          <div class="row header"><div>版本</div><div>状态</div><div>提交/保存时间</div><div>研发</div><div>说明</div><div>操作</div></div>
          ${versions.map(([version, status, time, owner, note, target]) => `
            <div class="row">
              <strong>${version}</strong>
              <div>${statusBadge(status)}</div>
              <div>${time}</div>
              <div>${owner}</div>
              <div>${note}</div>
              <a class="button secondary" href="#${target}">查阅</a>
            </div>
          `).join("")}
        </div>
        <div class="actions">
          <button data-route="experiment">返回当前实验单</button>
          <button class="secondary" data-route="archive">查看归档文件夹</button>
        </div>
      </div>
      <div class="card">
        <div class="section-title"><h3>A1 锁定版实验单摘要</h3><span class="badge green">只读</span></div>
        ${detailGrid([
          ["样品版本", "A1"],
          ["状态", "内部测试通过"],
          ["锁定时间", "2026-06-19 15:18"],
          ["研发出成", "89 kg"],
          ["参考包数", "178包"],
          ["得率", "84.55%"],
        ])}
        <div class="doc-grid" style="margin-top:16px">
          ${docCard("原辅料明细", "冻猪大肠头100kg / 去腥粉0.03kg / 香辛料包1.2kg")}
          ${docCard("工序记录", "清洗、保水、卤制、冷却、包装")}
          ${docCard("现场附件", "称重照片8张 / 成品照片4张 / 测试记录1份")}
          ${docCard("变更说明", "相对A0延长卤制时间，调整保水比例")}
          ${docCard("归档路径", "02_实验单/A1/锁定版")}
          ${docCard("审计记录", "保存、测试通过、锁定、归档均留痕")}
        </div>
      </div>
    </div>
  `;
}

function renderTestConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>内部测试人员配置</h3><span class="badge teal">可配置</span></div>
      <div class="form-grid">
        ${field("测试负责人", "品控 · 周婷", true)}
        ${field("参与测试人员", "研发总监、品控、业务代表", true)}
        ${field("测试项目", "口味 / 口感 / 出水 / 复热 / 外观", true)}
        ${field("飞书通知", "发送测试待办和消息卡片", true)}
      </div>
      <div class="field" style="margin-top:14px">
        <label>测试要求</label>
        <textarea>样品复热后30分钟内完成口味、口感、出水、外观确认；通过后锁定A0版本。</textarea>
      </div>
      <div class="actions">
        <button class="secondary" data-toast="测试配置已保存">保存配置</button>
        <button data-route="internal-test">发送测试通知</button>
      </div>
    </div>
  `;
}

function renderInternalTest() {
  return mobileLayout("内部测试确认", `品控 · ${state.product} A0`, `
    <span class="badge teal">待内部测试</span>
    ${field("复热方式", "微波 / 水浴", true)}
    ${field("口味评分", "8.5 / 10", true)}
    ${field("口感评价", "软糯度合适，咸香正常", true)}
    ${field("出水情况", "轻微出水，可接受", false)}
    ${field("测试结论", "内部测试通过，可提交并锁定版本", true, "textarea")}
    <div class="actions">
      <button class="green" data-route="shipment-list">测试通过，提交实验单并锁定A0</button>
      <button class="orange" data-route="experiment">不通过，退回复打样</button>
      <button class="red" data-route="stopped">停止打样</button>
    </div>
  `, `
    <div class="card">
      <h3>通过后的自动动作</h3>
      <p>A0实验单锁定，系统保存历史版本到产品文件夹；同时生成“待寄样反馈”列表记录，并可按需要生成核价文件。</p>
      <div class="actions">
        <button class="green" data-route="shipment-list">进入寄样反馈列表</button>
        <button class="secondary" data-route="pricing-list">直接生成核价文件</button>
      </div>
    </div>
  `);
}

function renderShipmentList() {
  return `
    <div class="card">
      <div class="section-title"><h3>寄样反馈列表</h3><span class="badge green">多个样品排队处理</span></div>
      <div class="table">
        <div class="row header"><div>产品</div><div>版本</div><div>客户</div><div>状态</div><div>日期</div><div>操作</div></div>
        ${[
          [state.product, "A0", "客户A", "待寄样反馈", "06-20", "shipment-detail"],
          ["黑椒鸡柳料理包", "A1", "客户C", "已寄样待反馈", "06-18", "shipment-detail"],
          ["调理鸡排", "A2", "客户D", "客户需复打样", "06-17", "experiment"],
        ].map(row).join("")}
      </div>
      <div class="actions">
        <button data-route="shipment-detail">进入选中样品反馈详情</button>
        <button class="secondary" data-route="pricing-list">样品完成后单独生成核价文件</button>
      </div>
    </div>
  `;
}

function renderShipmentDetail() {
  return mobileLayout("寄样反馈详情", `研发内勤/业务员 · ${state.product} A0`, `
    <span class="badge green">样品完成</span>
    ${field("寄样版本", "A0", true)}
    ${field("寄样数量", "6袋", true)}
    ${field("快递公司", "顺丰", true)}
    ${field("快递单号", "SF123456789", true)}
    ${field("客户反馈", "客户通过，准备进入报价和下单沟通。", false, "textarea")}
    <div class="actions">
      <button class="orange" data-route="experiment">客户不通过，继续打样</button>
      <button class="red" data-route="stopped">停止打样</button>
      <button data-route="pricing-list">客户通过，生成核价文件</button>
    </div>
  `, `
    <div class="card">
      <h3>为什么这里要列表 + 详情</h3>
      <p>同一时间可能有多个样品在寄样和等反馈，所以先进入列表定位样品，再进入详情页登记寄样、客户意见、是否继续打样或生成核价。</p>
      <div class="actions"><button data-route="pricing-list">进入核价文件列表</button></div>
    </div>
  `);
}

function renderPricingList() {
  return `
    <div class="card">
      <div class="section-title"><h3>核价文件列表</h3><span class="badge">Excel模板版本化</span></div>
      <div class="actions" style="margin-top:0;margin-bottom:14px">
        <button data-route="pricing-detail">为选中样品生成核价文件</button>
        <button class="secondary" data-toast="样品完成后可不等寄样反馈，单独生成核价文件">单独生成核价文件</button>
      </div>
      <div class="table">
        <div class="row header"><div>产品</div><div>样品版本</div><div>核价版本</div><div>状态</div><div>日期</div><div>操作</div></div>
        ${[
          [state.product, "A0", "待生成", "样品完成", "06-20", "pricing-detail"],
          ["黑椒鸡柳料理包", "A1", "A1-核价V2", "已提交财务", "06-18", "finance"],
          ["调理鸡排", "A2", "A2-核价V1", "草稿", "06-17", "pricing-detail"],
        ].map(row).join("")}
      </div>
    </div>
  `;
}

function renderPricingDetail() {
  return `
    <div class="card">
      <div class="section-title"><h3>核价文件详情</h3><span class="badge">生成 ${state.pricingVersion}.xlsx</span></div>
      ${detailGrid([
        ["产品", state.product],
        ["样品版本", "A0"],
        ["核价版本", "A0-核价V1"],
        ["Excel模板", "原材料清单A0"],
        ["生成来源", "样品完成/客户通过"],
        ["文件状态", "待提交财务"],
      ])}
      <div class="doc-grid" style="margin-top:16px">
        ${docCard("原料明细", "冻猪大肠头、去腥粉、香辛料")}
        ${docCard("辅料/包材", "调味料、500g袋、纸箱")}
        ${docCard("研发出成", "89kg / 178包 / 得率84.55%")}
      </div>
      <div class="actions">
        <button data-toast="已按Excel模板生成 A0-核价V1.xlsx">生成Excel核价文件</button>
        <button class="green" data-route="finance">提交财务并发送飞书通知</button>
      </div>
    </div>
  `;
}

function renderFinance() {
  return `
    <div class="card">
      <div class="section-title"><h3>通知财务核价</h3><span class="badge orange">流程终点</span></div>
      ${detailGrid([
        ["核价文件", "A0-核价V1.xlsx"],
        ["提交人", "研发内勤/系统"],
        ["通知方式", "飞书消息卡片"],
        ["财务状态", "待核价"],
        ["业务员", "李经理"],
        ["归档状态", "已同步留存"],
      ])}
      <div class="doc-grid" style="margin-top:16px">
        ${docCard("通知内容", "产品、版本、核价文件、研发出成、得率")}
        ${docCard("财务动作", "下载核价文件，进入财务核价流程")}
        ${docCard("系统留存", "通知时间、接收人、文件版本、操作日志")}
      </div>
      <div class="actions">
        <button class="secondary" data-toast="已再次提醒财务处理">提醒财务</button>
        <button data-route="archive">查看核价资料归档</button>
        <button class="green" data-route="dashboard">完成并返回工作台</button>
      </div>
    </div>
  `;
}

function renderArchive() {
  return `
    <div class="grid cols-2">
      <div class="timeline">
        <h3>版本与文件时间线</h3>
        ${timelineItem("需求单", "研发内勤录入 · 已审核")}
        ${timelineItem("A0实验单", "内部测试通过后锁定 · 可查阅")}
        ${timelineItem("A1实验单", "复打样锁定版 · 可查阅")}
        ${timelineItem("A2实验单", "草稿版 · 未锁定")}
        ${timelineItem("寄样反馈", "客户通过")}
        ${timelineItem("A0-核价V1", "已生成并通知财务")}
        ${timelineItem("核价资料", "已归档留存")}
      </div>
      <div class="card">
        <div class="section-title"><h3>${state.product} · 文件夹</h3><span class="badge green">归档完整</span></div>
        <div class="doc-grid">
          ${docCard("01_需求单", "研发内勤录入记录")}
          ${docCard("02_实验单历史版本", "A0锁定版 / A1锁定版 / A2草稿")}
          ${docCard("03_现场照片", "称重/工序/成品照片")}
          ${docCard("04_内部测试", "测试结论与签字")}
          ${docCard("05_寄样反馈", "快递与客户反馈")}
          ${docCard("06_核价文件", "A0-核价V1.xlsx / 财务通知记录")}
          ${docCard("07_审计日志", "操作人/时间/版本/通知状态")}
        </div>
        <div class="actions">
          <button data-route="experiment-history">查阅实验单历史版本</button>
          <button data-route="file-management">查看文件管理规则</button>
          <button class="secondary" data-route="dashboard">返回首页</button>
        </div>
      </div>
    </div>
  `;
}

function renderConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>人员配置与权限</h3><span class="badge">系统管理员维护</span></div>
      <div class="grid cols-3">
        ${roleCard("研发内勤", "录入需求、补充资料、寄样反馈、文件归档")}
        ${roleCard("研发总监", "审核需求、管理任务池、分发任务、查看全部")}
        ${roleCard("研发人员", "接受任务、填写实验单、保存草稿、上传照片")}
        ${roleCard("内部测试人员", "接收测试通知、填写测试结论、退回复打样")}
        ${roleCard("财务", "接收核价文件、维护报价状态、回传附件")}
        ${roleCard("管理员", "人员、权限、模板、文件夹和飞书配置")}
      </div>
      <div class="actions">
        <button data-toast="人员权限配置保存成功">保存权限配置</button>
        <button class="secondary" data-route="file-management">配置文件规则</button>
      </div>
    </div>
  `;
}

function renderDataModels() {
  const allModels = dataModelGroups.flatMap((group) => group.models.map((model) => ({ group: group.title, model })));

  return `
    <div class="card">
      <div class="section-title"><h3>数据模型确认清单</h3><span class="badge green">先确认模型，再确认字段</span></div>
      <p class="module-intro">这页只确认“有哪些数据模型”和“谁关联谁”。字段设计会按确认顺序逐个展开，避免一上来把所有表字段混在一起。</p>
      <div class="grid cols-4">
        ${stat("模型总数", "25", "var(--blue)")}
        ${stat("分组", "6", "var(--green)")}
        ${stat("优先确认", "3个", "var(--orange)")}
        ${stat("配置模型", "8个", "var(--purple)")}
      </div>
      <div class="model-flow">
        ${["主业务", "研发任务", "内部测试", "寄样核价", "文件审计", "配置中心"].map((name, index) => `
          <div class="model-flow-step ${index === 0 ? "active" : ""}">
            <span>${index + 1}</span>
            <strong>${name}</strong>
          </div>
        `).join("")}
      </div>
      <div class="model-layout">
        <div class="model-groups">
          ${dataModelGroups.map((group, index) => modelGroupCard(group, index)).join("")}
        </div>
        <div class="model-detail-card">
          <div class="section-title"><h3>当前建议先确认：主业务模型</h3><span class="badge orange">第1组</span></div>
          <div class="relation-chain">
            <div><strong>SampleRequest</strong><span>样品需求单</span></div>
            <em>审核通过生成</em>
            <div><strong>SampleProject</strong><span>样品项目/主档</span></div>
            <em>产生多个</em>
            <div><strong>SampleVersion</strong><span>A0 / A1 / A2</span></div>
          </div>
          <div class="config-section-grid" style="margin-top:16px">
            ${configList("确认重点", ["需求通过后是否一定生成项目", "项目编号和样品编号是否同一个", "版本号规则是否固定为A0/A1/A2", "是否允许一个需求拆多个样品项目"])}
            ${configList("后续依赖", ["研发任务必须关联样品项目", "实验单必须关联样品版本", "测试/寄样/核价都必须关联版本", "搜索和归档围绕项目+版本展开"])}
          </div>
          <div class="actions">
            <button data-toast="下一步确认 SampleRequest 样品需求单字段">开始确认 SampleRequest</button>
            <button class="secondary" data-route="workflow-config">查看流程状态配置</button>
          </div>
        </div>
      </div>
      <div class="section-title"><h3>全部模型清单</h3></div>
      <div class="table model-table">
        <div class="row header"><div>模型</div><div>中文名</div><div>分组</div><div>说明</div><div>状态</div><div>操作</div></div>
        ${allModels.map(({ group, model }, index) => `
          <div class="row">
            <strong>${model[0]}</strong>
            <div>${model[1]}</div>
            <div>${group.replace(/^[0-9. ]+/, "")}</div>
            <div>${model[2]}</div>
            <div>${index < 3 ? statusBadge("优先确认") : statusBadge("待确认")}</div>
            <button class="secondary" data-toast="后续将展开 ${model[0]} 字段确认">确认字段</button>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderFormConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>表单字段配置</h3><span class="badge">字段可开关 / 可排序</span></div>
      <div class="config-section-grid">
        ${configList("样品需求单", ["客户名称 必填", "产品名称 必填", "产品类型 字典", "规格要求 必填", "期望日期", "样品要求"])}
        ${configList("打样实验单", ["原辅料明细", "工序节点", "称重记录", "研发出成", "得率", "现场照片"])}
        ${configList("内部测试单", ["测试人员", "测试项目", "评分项", "测试结论", "通过/退回/停止"])}
        ${configList("寄样与核价", ["快递信息", "客户反馈", "核价版本", "Excel模板", "财务接收人"])}
      </div>
      <div class="actions">
        <button data-toast="字段配置已保存">保存字段配置</button>
        <button class="secondary" data-route="settings-module">返回配置中心</button>
      </div>
    </div>
  `;
}

function renderWorkflowConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>流程状态配置</h3><span class="badge green">状态机配置</span></div>
      <div class="table compact-table">
        <div class="row header"><div>节点</div><div>状态</div><div>触发按钮</div><div>规则</div><div>通知</div><div>操作</div></div>
        ${[
          ["需求审核", "待总监审核", "审核通过 / 退回补充", "通过后进任务池", "通知研发总监", "request-review"],
          ["任务分发", "待分发", "分发任务", "必须指定研发人员", "通知研发人员", "task-assign"],
          ["实验单", "打样中", "保存 / 通知测试", "保存可改，测试通过锁定", "通知测试人员", "experiment"],
          ["内部测试", "待内部测试", "通过 / 退回 / 停止", "通过后锁定版本", "通知内勤", "internal-test"],
          ["核价文件", "待生成核价", "生成 / 通知财务", "生成版本号不可覆盖", "通知财务", "pricing-detail"],
        ].map(row).join("")}
      </div>
      <div class="actions">
        <button data-toast="流程状态配置已保存">保存流程配置</button>
        <button class="secondary" data-route="flowchart">查看流程图</button>
      </div>
    </div>
  `;
}

function renderNotificationConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>飞书通知配置</h3><span class="badge orange">消息卡片规则</span></div>
      <div class="config-section-grid">
        ${configList("任务类通知", ["需求提交 -> 研发总监", "任务分发 -> 指定研发", "实验单保存 -> 研发本人", "测试通知 -> 测试人员"])}
        ${configList("结果类通知", ["测试通过 -> 研发内勤", "客户通过 -> 核价负责人", "核价生成 -> 财务", "停止打样 -> 研发总监"])}
        ${configList("提醒规则", ["到期前1天提醒", "逾期每天9点提醒", "财务未处理可催办", "消息卡片可跳转详情"])}
        ${configList("接收人来源", ["固定角色", "任务负责人", "配置人员组", "表单中选择人员"])}
      </div>
      <div class="actions">
        <button data-toast="飞书通知配置已保存">保存通知配置</button>
        <button class="secondary" data-route="settings-module">返回配置中心</button>
      </div>
    </div>
  `;
}

function renderTemplateConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>模板配置</h3><span class="badge purple">模板版本管理</span></div>
      <div class="table compact-table">
        <div class="row header"><div>模板</div><div>当前版本</div><div>使用节点</div><div>状态</div><div>更新日期</div><div>操作</div></div>
        ${[
          ["打样实验单模板", "V1.2", "实验单", "启用", "06-18", "experiment"],
          ["内部测试记录模板", "V1.0", "内部测试", "启用", "06-18", "internal-test"],
          ["核价Excel模板", "A0原料清单", "核价文件", "启用", "06-18", "pricing-detail"],
          ["寄样反馈模板", "V1.1", "寄样反馈", "启用", "06-17", "shipment-detail"],
        ].map(row).join("")}
      </div>
      <div class="actions">
        <button data-toast="模板配置已保存">保存模板配置</button>
        <button class="secondary" data-route="pricing-detail">查看核价模板效果</button>
      </div>
    </div>
  `;
}

function renderDictionaryConfig() {
  return `
    <div class="card">
      <div class="section-title"><h3>基础字典配置</h3><span class="badge gray">统一下拉选项</span></div>
      <div class="config-section-grid">
        ${configList("产品类型", ["冷冻即热菜", "腊制品", "生制调理品", "酱卤肉制品"])}
        ${configList("单位", ["kg", "g", "袋", "箱", "包"])}
        ${configList("样品状态", ["待审核", "任务池", "打样中", "待内部测试", "待寄样反馈", "待财务核价"])}
        ${configList("物料类别", ["主原料", "辅料", "调味料", "包材", "耗材"])}
      </div>
      <div class="actions">
        <button data-toast="基础字典已保存">保存字典</button>
        <button class="secondary" data-route="settings-module">返回配置中心</button>
      </div>
    </div>
  `;
}

function renderFileManagement() {
  return `
    <div class="card">
      <div class="section-title"><h3>文件管理与存档规则</h3><span class="badge gray">MinIO/NAS + 飞书云盘链接</span></div>
      <div class="form-grid">
        ${field("产品文件夹命名", "样品编号_产品名称_客户_创建日期", true)}
        ${field("版本规则", "A0、A1、A2；核价V1/V2；实验单锁定版/草稿版", true)}
        ${field("锁定规则", "内部测试通过后锁定实验单，不允许覆盖", true)}
        ${field("实验单历史", "每个样品版本单独保存，可查阅锁定版和草稿版", true)}
        ${field("归档位置", "MinIO/NAS主存储，飞书云盘保存协作链接", true)}
      </div>
      <div class="doc-grid" style="margin-top:16px">
        ${docCard("模板库", "实验单模板 / 核价Excel模板 / 测试记录模板")}
        ${docCard("版本库", "锁定版只读，新修改生成新版本")}
        ${docCard("审计日志", "创建、保存、提交、锁定、下载、通知")}
      </div>
      <div class="actions">
        <button data-toast="文件管理规则已保存">保存文件规则</button>
        <button class="secondary" data-route="flowchart">查看流程图</button>
      </div>
    </div>
  `;
}

function renderFlowchart() {
  const lanes = [
    ["工作台", "总览 / 待办 / 流程图入口", "dashboard", "blue", ["样品任务总览", "关键待办", "快捷入口"]],
    ["样品需求", "研发内勤录入，总监审核", "demand-module", "orange", ["录入需求", "总监审核", "通过进任务池"]],
    ["研发任务", "任务池、分发、实验、测试锁版", "rnd-module", "green", ["任务池", "分发并飞书通知", "研发接受任务", "实验单保存", "历史版本可查阅", "通知内部测试", "测试通过并锁定版本"]],
    ["寄样核价", "寄样反馈、核价文件和财务通知", "shipment-pricing-module", "purple", ["寄样反馈列表", "寄样反馈详情", "单独生成核价文件", "核价文件列表", "生成核价文件", "通知财务核价", "核价资料归档"]],
    ["系统设置", "人员、权限、文件和模板规则", "settings-module", "gray", ["人员权限", "测试人员规则", "文件归档规则", "核价模板规则"]],
  ];

  return `
    <div class="card">
      <div class="section-title"><h3>飞书自建应用研发样品流转图</h3><span class="badge">业务模块式</span></div>
      <div class="swimlane-board">
        ${lanes.map(([moduleName, desc, target, color, steps], laneIndex) => `
          <a class="swimlane ${color}" href="#${target}">
            <div class="lane-head">
              <span>${String(laneIndex + 1).padStart(2, "0")}</span>
              <strong>${moduleName}</strong>
              <small>${desc}</small>
            </div>
            <div class="lane-steps">
              ${steps.map((step) => `<em>${step}</em>`).join("")}
            </div>
          </a>
          ${laneIndex < lanes.length - 1 ? `<div class="flow-arrow">↓</div>` : ""}
        `).join("")}
      </div>
      <div class="grid cols-3" style="margin-top:18px">
        ${stat("一级模块", "5", "var(--blue)")}
        ${stat("锁版节点", "测试通过", "var(--teal)")}
        ${stat("列表节点", "寄样/核价", "var(--orange)")}
      </div>
      <div class="actions">
        <button data-route="request-new">按流程体验一遍</button>
        <button class="secondary" data-route="settings-module">查看系统设置</button>
      </div>
    </div>
  `;
}

function renderStopped() {
  return `
    <div class="card">
      <div class="section-title"><h3>停止/废弃项目池</h3><span class="badge red">已停止</span></div>
      ${detailGrid([
        ["产品名称", "试制香辣肥肠"],
        ["最后版本", "A2"],
        ["停止原因", "客户取消需求"],
        ["停止人", "研发总监"],
        ["停止时间", "2026-06-18"],
        ["归档状态", "已归档"],
      ])}
      <div class="field" style="margin-top:16px">
        <label>停止说明</label>
        <textarea>停止后不删除实验数据、照片、测试记录和核价草稿；进入废弃项目池，后续可复制为新需求重新发起。</textarea>
      </div>
      <div class="actions"><button class="secondary" data-route="dashboard">返回首页</button></div>
    </div>
  `;
}

function row([a, b, c, d, e, target]) {
  return `<div class="row"><strong>${a}</strong><div>${b}</div><div>${c}</div><div>${statusBadge(d)}</div><div>${e}</div><a class="button secondary" href="#${target}">查看</a></div>`;
}

function modulePage(title, intro, steps, metrics) {
  return `
    <div class="card">
      <div class="section-title"><h3>${title}</h3><span class="badge">模块首页</span></div>
      <p class="module-intro">${intro}</p>
      <div class="grid cols-3">
        ${metrics.map(([label, value, color]) => stat(label, value, color)).join("")}
      </div>
      <div class="section-title"><h3>子功能与处理顺序</h3></div>
      <div class="step-grid">
        ${steps.map(([index, titleText, desc, target, color]) => stepCard(index, titleText, desc, target, color)).join("")}
      </div>
    </div>
  `;
}

function moduleCard(title, desc, target, color) {
  return `
    <a class="module-card ${color}" href="#${target}">
      <strong>${title}</strong>
      <span>${desc}</span>
    </a>
  `;
}

function stepCard(index, title, desc, target, color) {
  return `
    <a class="step-card ${color}" href="#${target}">
      <span>${index}</span>
      <strong>${title}</strong>
      <small>${desc}</small>
    </a>
  `;
}

function configCard(title, desc, target, color, scope) {
  return `
    <a class="config-card ${color}" href="#${target}">
      <div>
        <strong>${title}</strong>
        <small>${desc}</small>
      </div>
      <span>影响：${scope}</span>
    </a>
  `;
}

function configList(title, items) {
  return `
    <div class="config-list">
      <h4>${title}</h4>
      ${items.map((item) => `<p>${item}</p>`).join("")}
    </div>
  `;
}

function modelGroupCard(group, index) {
  return `
    <div class="model-group-card ${index === 0 ? "active" : ""}">
      <div class="model-group-head">
        <strong>${group.title}</strong>
        <span>${group.status}</span>
      </div>
      <small>${group.scope}</small>
      <div class="model-tags">
        ${group.models.map(([code]) => `<em>${code}</em>`).join("")}
      </div>
    </div>
  `;
}

function taskTable(rows = sampleRows) {
  return `
    <div class="table">
      <div class="row header"><div>产品</div><div>版本</div><div>负责人</div><div>当前状态</div><div>期限</div><div>操作</div></div>
      ${rows.map(row).join("")}
    </div>
  `;
}

function statusBadge(status) {
  const color = status.includes("测试") ? "teal" : status.includes("寄样") ? "green" : status.includes("核价") ? "orange" : status.includes("分发") || status.includes("任务池") ? "gray" : "green";
  return `<span class="badge ${color}">${status}</span>`;
}

function formatShortDate(date) {
  return date.slice(5);
}

function stat(label, value, color) {
  return `<div class="stat"><span>${label}</span><strong style="color:${color}">${value}</strong></div>`;
}

function field(label, value, required = false, type = "input") {
  const control = type === "textarea"
    ? `<textarea>${value}</textarea>`
    : `<input value="${value}" />`;
  return `<div class="field"><label>${required ? `<span style="color:var(--red)">*</span>` : ""}${label}</label>${control}</div>`;
}

function detailGrid(items) {
  return `<div class="grid cols-3">${items.map(([k, v]) => `<div class="stat"><span>${k}</span><strong style="font-size:20px;color:var(--ink)">${v}</strong></div>`).join("")}</div>`;
}

function mobileLayout(title, subtitle, phoneBody, sideBody, home = false) {
  return `
    <div class="mobile-stage">
      <div class="phone">
        <div class="phone-screen">
          <div class="phone-notch"></div>
          <div class="phone-title">${title}</div>
          <div class="phone-sub">${subtitle}</div>
          ${phoneBody}
          ${home ? `<div class="bottom-tabs"><div class="tab active">首页</div><div class="tab">任务</div><div class="tab">档案</div><div class="tab">我的</div></div>` : ""}
        </div>
      </div>
      ${sideBody}
    </div>
  `;
}

function phoneCard(title, body, meta, tag, color, target) {
  return `
    <a class="phone-card" href="#${target}" style="display:block">
      <div style="display:flex;justify-content:space-between;gap:12px">
        <div><h4>${title}</h4><p>${body}</p><p>${meta}</p></div>
        <span class="badge ${color}">${tag}</span>
      </div>
    </a>
  `;
}

function timelineItem(title, body) {
  return `<div class="timeline-item"><strong>${title}</strong><div style="color:var(--muted);font-weight:700;margin-top:5px">${body}</div></div>`;
}

function docCard(title, meta) {
  return `<div class="doc-card"><strong>${title}</strong><small>${meta}</small></div>`;
}

function taskCard(title, version, owner, status) {
  return `<div class="card"><h3>${title}</h3><p>${version}</p><p style="color:var(--muted)">${owner}</p><span class="badge orange">${status}</span></div>`;
}

function roleCard(role, desc) {
  return `<div class="doc-card"><strong>${role}</strong><small>${desc}</small></div>`;
}

function render() {
  const view = viewById(routeId());
  document.querySelector("#app").innerHTML = appShell(view);
  bindActions();
}

function bindActions() {
  document.querySelectorAll("[data-route]").forEach((el) => {
    el.addEventListener("click", () => go(el.dataset.route));
  });
  document.querySelectorAll("[data-toast]").forEach((el) => {
    el.addEventListener("click", () => toast(el.dataset.toast));
  });
  document.querySelectorAll("#search-name, #search-start, #search-end").forEach((el) => {
    el.addEventListener("input", filterSampleRecords);
    el.addEventListener("change", filterSampleRecords);
  });
  document.querySelectorAll("[data-search-action]").forEach((el) => {
    el.addEventListener("click", () => {
      if (el.dataset.searchAction === "reset") {
        resetSampleSearch();
      } else {
        filterSampleRecords();
      }
    });
  });
}

function toast(message) {
  const el = document.querySelector("#toast");
  el.textContent = message;
  el.classList.add("show");
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => el.classList.remove("show"), 1800);
}

window.addEventListener("hashchange", render);
render();
