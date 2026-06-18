import fs from "node:fs/promises";
import sharp from "sharp";

const input = "diagrams/飞书自建应用_研发样品管理平台_总体逻辑架构图.svg";
const output = "diagrams/飞书自建应用_研发样品管理平台_总体逻辑架构图.png";

const svg = await fs.readFile(input);
await sharp(svg, { density: 180 })
  .png()
  .toFile(output);

console.log(output);
