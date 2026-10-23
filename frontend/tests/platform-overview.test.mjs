import assert from "node:assert/strict";
import { test } from "node:test";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta } from "../src/utils/platformOverview.js";

test("unknown metrics are not confused with measured zero", () => {
  assert.equal(formatPlatformMetric(null), "—");
  assert.equal(formatPlatformMetric(undefined), "—");
  assert.equal(formatPlatformMetric(0), "0");
  assert.equal(formatPlatformMetric(1961), "1,961");
  assert.equal(formatPlatformMetric(NaN), "—");
});
test("codeforces rating colors are not applied to other platform ratings", () => {
  assert.equal(platformRatingMeta("codeforces",1961).color,"#aa00aa");
  assert.equal(platformRatingMeta("codeforces",2040).label,"Candidate Master");
  assert.equal(platformRatingMeta("atcoder",1961).label,"");
  assert.equal(platformRatingMeta("luogu",null).label,"");
});
test("a previous account snapshot never appears after rebinding", () => {
  const overview={accountId:"old",connected:true,rating:1961};
  assert.equal(matchingPlatformOverview(overview,"new"),null);
  assert.equal(matchingPlatformOverview(overview,null),null);
  assert.equal(matchingPlatformOverview({...overview,connected:false},"old"),null);
  assert.equal(matchingPlatformOverview(overview,"old"),overview);
});
