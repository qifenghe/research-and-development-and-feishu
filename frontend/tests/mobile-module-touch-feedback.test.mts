import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const mobileStyles = readFileSync(
  new URL("../apps/mobile/src/styles/components.css", import.meta.url),
  "utf8",
);

test("mobile module cards suppress the native translucent tap block", () => {
  assert.match(
    mobileStyles,
    /\.hero-card,\s*\.task-card--link,\s*\.role-entry-card\s*\{[\s\S]*?-webkit-tap-highlight-color:\s*transparent;/,
  );
  assert.match(
    mobileStyles,
    /\.hero-card,\s*\.task-card--link,\s*\.role-entry-card\s*\{[\s\S]*?-webkit-touch-callout:\s*none;/,
  );
  assert.match(
    mobileStyles,
    /@media\s*\(hover:\s*none\)\s*and\s*\(pointer:\s*coarse\)\s*\{[\s\S]*?\.hero-card:focus:not\(:focus-visible\),[\s\S]*?\.task-card--link:focus:not\(:focus-visible\),[\s\S]*?\.role-entry-card:focus:not\(:focus-visible\)/,
  );
});
