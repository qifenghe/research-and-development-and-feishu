import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = process.argv[2];
if (!inputPath) {
  throw new Error("Usage: node inspect_sample_workbook.mjs <xlsx>");
}

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

for (const range of [
  "A1:O20",
  "A21:O60",
  "A61:O100",
]) {
  const result = await workbook.inspect({
    kind: "table",
    range,
    include: "values,formulas,formats",
    tableMaxRows: 60,
    tableMaxCols: 20,
  });
  console.log(`\n--- ${range} ---`);
  console.log(result.ndjson);
}

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula errors",
});
console.log("\n--- formula errors ---");
console.log(errors.ndjson);
