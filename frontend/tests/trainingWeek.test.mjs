import assert from "node:assert/strict";
import { test } from "node:test";
import { mondayOf, shiftWeek } from "../src/utils/trainingWeek.js";

test("weeks run Monday to Sunday including the Sunday year boundary", () => {
  assert.equal(mondayOf("2027-01-03"), "2026-12-28");
  assert.equal(mondayOf("2027-01-04"), "2027-01-04");
  assert.equal(mondayOf("2026-10-15"), "2026-10-12");
});

test("week navigation crosses years and leap-day months without local timezone shifts", () => {
  assert.equal(shiftWeek("2026-12-28", 1), "2027-01-04");
  assert.equal(shiftWeek("2024-03-04", -1), "2024-02-26");
  assert.equal(shiftWeek("2026-10-15", -1), "2026-10-05");
});

test("invalid or temporarily empty date inputs do not create a training week", () => {
  for (const input of [
    "",
    null,
    undefined,
    "2026-02-30",
    "2026-13-01",
    "bad-date",
  ]) {
    assert.equal(mondayOf(input), "");
    assert.equal(shiftWeek(input, 1), "");
  }
});
