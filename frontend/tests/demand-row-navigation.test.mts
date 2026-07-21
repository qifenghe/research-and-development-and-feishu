import assert from "node:assert/strict";
import test from "node:test";

import { createDemandRowProps } from "../apps/pc/src/views/demand/demand-row-navigation.ts";

test("clicking a demand row opens its detail", () => {
  let openedId = "";
  const props = createDemandRowProps("REQ-001", (id) => {
    openedId = id;
  });

  props.onClick();

  assert.equal(openedId, "REQ-001");
  assert.equal(props.style.cursor, "pointer");
});
