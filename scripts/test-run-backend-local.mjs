import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  buildBackendCommand,
  buildBackendEnvironment,
  parseRunBackendArgs,
  readBackendLocalEnv,
} from "./run-backend-local.mjs";

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "rnd-backend-env-"));
const envFile = path.join(tempDir, ".env.feishu.local");
fs.writeFileSync(envFile, `
# local Feishu config
FEISHU_MODE=OPENAPI
FEISHU_APP_ID=cli_test_123
FEISHU_APP_SECRET="secret value"
FEISHU_APP_URL=https://rnd.example.com/
FEISHU_CARD_ACTION_SECRET=card-secret
SESSION_SECRET=session-secret
`);

const env = readBackendLocalEnv(envFile);
assert.equal(env.FEISHU_MODE, "OPENAPI");
assert.equal(env.FEISHU_APP_SECRET, "secret value");

const merged = buildBackendEnvironment({
  baseEnv: { PATH: "/usr/bin", FEISHU_MODE: "MOCK" },
  envFile,
});
assert.equal(merged.PATH, "/usr/bin");
assert.equal(merged.FEISHU_MODE, "OPENAPI");
assert.equal(merged.FEISHU_APP_ID, "cli_test_123");
assert.equal(merged.FEISHU_APP_SECRET, "secret value");

const command = buildBackendCommand({
  javaHome: "/opt/jdk17",
  backendDir: "/repo/backend",
  port: "8081",
});
assert.equal(command.command, "mvn");
assert.deepEqual(command.args, ["spring-boot:run", "-Dspring-boot.run.arguments=--server.port=8081"]);
assert.equal(command.cwd, "/repo/backend");
assert.equal(command.env.JAVA_HOME, "/opt/jdk17");

const parsed = parseRunBackendArgs([
  "--env-file", "backend/.env.feishu.local",
  "--backend-dir", "backend",
  "--port", "8081",
]);
assert.equal(parsed.envFile, "backend/.env.feishu.local");
assert.equal(parsed.backendDir, "backend");
assert.equal(parsed.port, "8081");

console.log("Run backend local tests passed.");
