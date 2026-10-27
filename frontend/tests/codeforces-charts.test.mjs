import assert from "node:assert/strict";
import { test } from "node:test";
import { tagDistribution, ratingDistribution, difficultyColor } from "../src/utils/codeforcesCharts.js";

test("rating buckets retain gaps, unusual difficulties and unrated problems", () => {
  const result = ratingDistribution([{ rating: 800, count: 2 }, { rating: 1900, count: 3 },
    { rating: 1900, count: 4 }, { rating: null, count: 5 }, { rating: 4000, count: 1 },
    { rating: 900, count: -1 }, { rating: "1000", count: 7 }]);
  assert.equal(result.rated, 10);
  assert.equal(result.unrated, 5);
  assert.deepEqual(result.peak, { rating: 1900, count: 7 });
  assert.deepEqual(result.bars.find(item => item.rating === 900), { rating: 900, count: 0 });
  assert.equal(result.bars.at(-1).rating, 4000);
  assert.ok(result.ceiling >= 7);
});

test("legacy missing chart and measured empty chart are distinct", () => {
  assert.equal(ratingDistribution(undefined), null);
  assert.equal(ratingDistribution(null), null);
  const result = ratingDistribution([]);
  assert.equal(result.bars.length, 28);
  assert.equal(result.rated, 0);
  assert.equal(result.peak, null);
  assert.equal(result.ceiling, 4);
  assert.equal(ratingDistribution([{ rating: null, count: 9 }]).unrated, 9);
});

test("rating axis always includes the largest count and uses integer ticks", () => {
  for (const count of [1, 2, 3, 5, 101, 297, 10000]) {
    const result = ratingDistribution([{rating: 800, count}]);
    assert.ok(result.ceiling >= count);
    assert.ok(Number.isFinite(result.ceiling));
    assert.ok(Number.isInteger(result.ceiling / 4));
  }
});

test("tag grouping is sorted, bounded and preserves total without mutating input", () => {
  const source = Array.from({ length: 15 }, (_, index) => ({ tag: `tag-${index}`, count: index + 1 }));
  const result = tagDistribution(source);
  assert.equal(result.length, 12);
  assert.equal(result[0].tag, "tag-14");
  assert.deepEqual(result.at(-1), { tag: "其他", count: 10 });
  assert.equal(result.reduce((sum, item) => sum + item.count, 0), 120);
  assert.equal(source[0].tag, "tag-0");
  assert.deepEqual(tagDistribution([{tag: "bad",count:NaN}]), []);
});

test("difficulty colors follow rating bands", () => {
  assert.equal(difficultyColor(800), difficultyColor(1100));
  assert.notEqual(difficultyColor(1100), difficultyColor(1200));
  assert.equal(difficultyColor(1600), difficultyColor(1800));
  assert.notEqual(difficultyColor(1800), difficultyColor(1900));
});
