import test from "node:test";
import assert from "node:assert/strict";
import { buildTagDistribution } from "../src/utils/codeforcesCharts.js";

test("tag bars retain every tag and tie order is independent of the response order", () => {
  const source = [{ tag: "math", count: 5 }, { tag: "dp", count: 5 }, { tag: "graphs", count: 10 }];
  const result = buildTagDistribution(source);
  assert.deepEqual(result.items.map(item => item.tag), ["graphs", "dp", "math"]);
  assert.deepEqual(buildTagDistribution([...source].reverse()), result);
  assert.deepEqual(source.map(item => item.tag), ["math", "dp", "graphs"]);
  assert.equal(buildTagDistribution(Array.from({ length: 30 }, (_, index) => ({ tag: `tag-${index}`, count: index }))).items.length, 30);
});

test("bar widths represent counts against the maximum without a zero-value stub", () => {
  const result = buildTagDistribution([{ tag: "max", count: 10 }, { tag: "half", count: 5 }, { tag: "zero", count: 0 }]);
  assert.deepEqual(result.items.map(item => item.widthPercent), [100, 50, 0]);
  assert.equal(result.totalTagCount, 15);
  assert.ok(Math.abs(result.items.reduce((sum, item) => sum + item.sharePercent, 0) - 100) < 0.0001);
});

test("empty tag data has no invented total, percentage or width", () => {
  assert.deepEqual(buildTagDistribution(), { totalTagCount: 0, items: [] });
  const zero = buildTagDistribution([{ tag: "dp", count: 0 }]);
  assert.equal(zero.totalTagCount, 0);
  assert.equal(zero.items[0].widthPercent, 0);
  assert.equal(zero.items[0].sharePercent, 0);
});
