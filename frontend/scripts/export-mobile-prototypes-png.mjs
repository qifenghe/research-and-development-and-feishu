import { chromium } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const pagesDir = path.join(__dirname, "../../prototypes/pages");
const pngDir = path.join(pagesDir, "png");
fs.mkdirSync(pngDir, { recursive: true });

const exports = [
  { file: "00_手机端_页面总览.svg", width: 2400, height: 2000 },
  { file: "01_手机端_我的待办.svg", width: 520, height: 980 },
  { file: "02_手机端_任务详情.svg", width: 520, height: 980 },
  { file: "03_手机端_打样实验单.svg", width: 520, height: 980 },
  { file: "04_手机端_测试确认.svg", width: 520, height: 980 },
  { file: "09_手机端_寄样反馈.svg", width: 520, height: 980 },
  { file: "10_手机端_样品搜索.svg", width: 520, height: 980 },
  { file: "11_手机端_我的.svg", width: 520, height: 980 },
  { file: "12_手机端_实验单历史.svg", width: 520, height: 980 },
];

const browser = await chromium.launch();
const context = await browser.newContext({ deviceScaleFactor: 2 });

for (const item of exports) {
  const svgPath = path.join(pagesDir, item.file);
  if (!fs.existsSync(svgPath)) {
    console.warn("skip missing:", item.file);
    continue;
  }
  const pngName = item.file.replace(/\.svg$/, ".png");
  const pngPath = path.join(pngDir, pngName);
  const page = await context.newPage();
  await page.setViewportSize({ width: item.width, height: item.height });
  await page.goto(`file://${svgPath}`, { waitUntil: "load" });
  await page.waitForTimeout(500);
  await page.screenshot({ path: pngPath, fullPage: false });
  await page.close();
  console.log("wrote", pngPath);
}

await browser.close();
console.log("done:", pngDir);
