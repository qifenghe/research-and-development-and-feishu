import assert from "node:assert/strict";
import test from "node:test";

import { canAccessRoute, canPerformAction, roleLabel } from "../packages/shared/src/permissions/index.ts";

test("only super administrators can access settings management", () => {
  assert.equal(canAccessRoute("SYSTEM_ADMIN", "/settings/users", "pc"), true);
  assert.equal(canPerformAction("SYSTEM_ADMIN", "MANAGE_SETTINGS"), true);
  assert.equal(canAccessRoute("RND_DIRECTOR", "/settings/users", "pc"), false);
  assert.equal(canPerformAction("RND_DIRECTOR", "MANAGE_SETTINGS"), false);
});

test("system administrator uses the super administrator label", () => {
  assert.equal(roleLabel("SYSTEM_ADMIN"), "超级管理员");
});
test('SOP revision routes are restricted to internal assistant and director',()=>{
 assert.equal(canAccessRoute('RND_ASSISTANT','/sops','pc'),true);
 for(const role of ['RND_ASSISTANT','RND_DIRECTOR'])assert.equal(canAccessRoute(role as any,'/sops/amendments/S1','pc'),true);
 for(const role of ['RND_ENGINEER','FINANCE','TESTER'])assert.equal(canAccessRoute(role as any,'/sops/amendments/S1','pc'),false);
});
