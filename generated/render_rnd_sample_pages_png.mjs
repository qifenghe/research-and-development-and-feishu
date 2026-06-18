import fs from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const dir = "prototypes/pages";
const files = (await fs.readdir(dir))
  .filter((file) => file.endsWith(".svg"))
  .sort();

for (const file of files) {
  const input = path.join(dir, file);
  const output = path.join(dir, file.replace(/\.svg$/, ".png"));
  const svg = await fs.readFile(input);
  await sharp(svg, { density: 180 }).png().toFile(output);
  console.log(output);
}
