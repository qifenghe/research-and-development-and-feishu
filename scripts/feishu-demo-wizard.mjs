import process from "node:process";
import fs from "node:fs";
import { pathToFileURL } from "node:url";

import {
  buildReadinessAdvice,
  parseEnvText,
  runCheck,
} from "./feishu-local-check.mjs";
import { runDemoTaskFlow } from "./feishu-demo-task.mjs";

const DEFAULT_DEMO_CONFIG_FILE = ".feishu-demo.local";

export async function runDemoWizard(options = {}) {
  const check = options.check || runCheck;
  const demo = options.demo || runDemoTaskFlow;
  const fileOptions = loadOptionalDemoWizardConfig(options.demoConfigFile || DEFAULT_DEMO_CONFIG_FILE);
  const mergedOptions = mergeWizardOptions(fileOptions, options);
  const checkResult = await check({
    envFile: mergedOptions.envFile,
    backendUrl: mergedOptions.backendUrl,
  });
  const advice = buildReadinessAdvice(checkResult);
  if (!checkResult.ready) {
    return {
      ready: false,
      advice,
      checkResult,
      demoResult: null,
    };
  }
  const demoResult = await demo({
    backendUrl: mergedOptions.backendUrl,
    dispatch: mergedOptions.dispatch === true,
    productName: mergedOptions.productName,
    customerName: mergedOptions.customerName,
    assistant: mergedOptions.assistant,
    director: mergedOptions.director,
    engineer: mergedOptions.engineer,
    testerName: mergedOptions.testerName,
    financeRecipientName: mergedOptions.financeRecipientName,
  });
  return {
    ready: true,
    advice,
    checkResult,
    demoResult,
  };
}

export function loadDemoWizardConfig(filePath) {
  const env = parseEnvText(fs.readFileSync(filePath, "utf8"));
  return envToWizardOptions(env);
}

export function buildWizardSummary(result) {
  return {
    ready: result.ready,
    adviceCode: result.advice.code,
    requestId: result.demoResult?.requestId || null,
    taskId: result.demoResult?.taskId || null,
    experimentFormId: result.demoResult?.experimentFormId || null,
    testAssignmentId: result.demoResult?.testAssignmentId || null,
    versionId: result.demoResult?.versionId || null,
    shipmentId: result.demoResult?.shipmentId || null,
    pricingFileId: result.demoResult?.pricingFileId || null,
    financeNotificationId: result.demoResult?.financeNotificationId || null,
    sampleNo: result.demoResult?.sampleNo || null,
    pendingCount: result.demoResult?.pendingNotifications?.length ?? null,
    dispatchResult: result.demoResult?.dispatchResult || null,
  };
}

function loadOptionalDemoWizardConfig(filePath) {
  if (!filePath || !fs.existsSync(filePath)) {
    return {};
  }
  return loadDemoWizardConfig(filePath);
}

function envToWizardOptions(env) {
  return {
    assistant: personFromEnv(env, "FEISHU_DEMO_ASSISTANT"),
    director: personFromEnv(env, "FEISHU_DEMO_DIRECTOR"),
    engineer: personFromEnv(env, "FEISHU_DEMO_ENGINEER"),
    productName: env.FEISHU_DEMO_PRODUCT_NAME,
    customerName: env.FEISHU_DEMO_CUSTOMER_NAME,
    testerName: env.FEISHU_DEMO_TESTER_NAME,
    financeRecipientName: env.FEISHU_DEMO_FINANCE_RECIPIENT_NAME,
  };
}

function personFromEnv(env, prefix) {
  const person = {};
  if (env[`${prefix}_NAME`]) {
    person.name = env[`${prefix}_NAME`];
  }
  if (env[`${prefix}_USER_ID`]) {
    person.feishuUserId = env[`${prefix}_USER_ID`];
  }
  return Object.keys(person).length > 0 ? person : undefined;
}

function mergeWizardOptions(fileOptions, commandOptions) {
  return {
    ...fileOptions,
    ...commandOptions,
    assistant: {
      ...(fileOptions.assistant || {}),
      ...(commandOptions.assistant || {}),
    },
    director: {
      ...(fileOptions.director || {}),
      ...(commandOptions.director || {}),
    },
    engineer: {
      ...(fileOptions.engineer || {}),
      ...(commandOptions.engineer || {}),
    },
  };
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[++index];
    } else if (arg === "--demo-config-file") {
      options.demoConfigFile = argv[++index];
    } else if (arg === "--backend-url") {
      options.backendUrl = argv[++index];
    } else if (arg === "--assistant-name") {
      options.assistant = { ...(options.assistant || {}), name: argv[++index] };
    } else if (arg === "--assistant-feishu-user-id") {
      options.assistant = { ...(options.assistant || {}), feishuUserId: argv[++index] };
    } else if (arg === "--director-name") {
      options.director = { ...(options.director || {}), name: argv[++index] };
    } else if (arg === "--director-feishu-user-id") {
      options.director = { ...(options.director || {}), feishuUserId: argv[++index] };
    } else if (arg === "--engineer-name") {
      options.engineer = { ...(options.engineer || {}), name: argv[++index] };
    } else if (arg === "--engineer-feishu-user-id") {
      options.engineer = { ...(options.engineer || {}), feishuUserId: argv[++index] };
    } else if (arg === "--product-name") {
      options.productName = argv[++index];
    } else if (arg === "--customer-name") {
      options.customerName = argv[++index];
    } else if (arg === "--tester-name") {
      options.testerName = argv[++index];
    } else if (arg === "--finance-recipient-name") {
      options.financeRecipientName = argv[++index];
    } else if (arg === "--dispatch") {
      options.dispatch = true;
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    }
  }
  return options;
}

function printResult(result) {
  console.log("飞书联调向导");
  console.log(`- 就绪状态：${result.ready ? "已就绪" : "未就绪"}`);
  console.log(`- 下一步：${result.advice.message}`);
  if (!result.ready) {
    process.exitCode = 1;
    return;
  }
  const summary = buildWizardSummary(result);
  console.log(`- 样品需求：${summary.requestId}`);
  console.log(`- 样品编号：${summary.sampleNo}`);
  console.log(`- 研发任务：${summary.taskId}`);
  console.log(`- 实验单：${summary.experimentFormId}`);
  console.log(`- 测试任务：${summary.testAssignmentId}`);
  console.log(`- 样品版本：${summary.versionId}`);
  console.log(`- 寄样记录：${summary.shipmentId}`);
  console.log(`- 核价文件：${summary.pricingFileId}`);
  console.log(`- 财务通知：${summary.financeNotificationId}`);
  console.log(`- 待发送通知数：${summary.pendingCount}`);
  if (summary.dispatchResult) {
    console.log(`- 派发结果：attempted=${summary.dispatchResult.attemptedCount}, sent=${summary.dispatchResult.sentCount}, failed=${summary.dispatchResult.failedCount}`);
  } else {
    console.log("- 当前只生成待发送通知；确认真实发送时追加 --dispatch。");
  }
}

function printHelp() {
  console.log(`用法：
  node scripts/feishu-demo-wizard.mjs \\
    --demo-config-file .feishu-demo.local \\
    --backend-url http://127.0.0.1:8080 \\
    --assistant-name 李内勤 \\
    --assistant-feishu-user-id ou_assistant_xxx \\
    --director-name 王总监 \\
    --director-feishu-user-id ou_director_xxx \\
    --engineer-name 张研发 \\
    --engineer-feishu-user-id ou_xxx \\
    --tester-name 内部测试员 \\
    --finance-recipient-name 财务核价员

说明：
  - 先运行飞书本地自检，未就绪时不会创建演示任务。
  - 就绪后自动创建样品需求、审核、分发、接受任务、实验、测试通过、寄样反馈、核价文件和财务通知。
  - 默认会尝试读取 .feishu-demo.local；也可用 --demo-config-file 指定本地演示人员配置。
  - 可分别指定研发内勤、研发总监和研发人员的飞书 user_id，并配置测试人员、财务接收人姓名。
  - 默认不真实派发；追加 --dispatch 后才调用通知派发接口。
  - 使用 --dispatch 时必须填写真实研发人员飞书 user_id。`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
  } else {
    runDemoWizard(options)
      .then(printResult)
      .catch((error) => {
        console.error(`飞书联调向导失败：${error.message}`);
        process.exitCode = 1;
      });
  }
}
