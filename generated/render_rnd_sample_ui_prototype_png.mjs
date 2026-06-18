import fs from "node:fs/promises";
import sharp from "sharp";

const input = "prototypes/飞书自建应用研发样品管理系统_界面原型图.svg";
const output = "prototypes/飞书自建应用研发样品管理系统_界面原型图.png";

const svg = await fs.readFile(input);
await sharp(svg, { density: 180 })
  .png()
  .toFile(output);

console.log(output);
