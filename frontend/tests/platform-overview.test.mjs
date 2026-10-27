import assert from "node:assert/strict";
import { test } from "node:test";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta, needsPlatformPolling, platformAccountLabel } from "../src/utils/platformOverview.js";

test("unknown metrics are not confused with measured zero", () => {
  assert.equal(formatPlatformMetric(null), "—");
  assert.equal(formatPlatformMetric(undefined), "—");
  assert.equal(formatPlatformMetric(0), "0");
  assert.equal(formatPlatformMetric(1961), "1,961");
  assert.equal(formatPlatformMetric(NaN), "—");
});

test("UID platforms prefer their verified site's nickname while retaining an ID fallback", () => {
  for (const platform of ["luogu", "nowcoder"]) {
    const row = { platform, connected:true, accountId:"123", displayName:"  猫头鹰  " };
    assert.equal(platformAccountLabel(platform, "123", row), "猫头鹰");
    assert.equal(platformAccountLabel(platform, "456", row), "UID 456");
    assert.equal(platformAccountLabel(platform, "123", {...row,connected:false}), "UID 123");
    assert.equal(platformAccountLabel(platform, "123", {...row,platform:"another"}), "UID 123");
    for (const displayName of [null, undefined, "", "  ", 12, {}])
      assert.equal(platformAccountLabel(platform, "123", {...row,displayName}), "UID 123");
    assert.equal(platformAccountLabel(platform, null, row), null);
  }
});

test("handles and profile links never change to nickname values", () => {
  for (const platform of ["codeforces", "atcoder"])
    assert.equal(platformAccountLabel(platform, "handle", {connected:true,accountId:"handle",displayName:"Other Name"}), "handle");
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

test("only bound pending and syncing statistics need bounded polling", () => {
  assert.equal(needsPlatformPolling([{connected:true,dataStatus:"PENDING"}]),true);
  assert.equal(needsPlatformPolling([{connected:true,dataStatus:"SYNCING"}]),true);
  for (const status of ["UNBOUND", "UNAVAILABLE", "READY", "NOT_SUPPORTED"])
    assert.equal(needsPlatformPolling([{connected:true,dataStatus:status}]),false);
  assert.equal(needsPlatformPolling([{connected:false,dataStatus:"PENDING"}]),false);
});

test("home uses the four-platform panel and binding reuses the local statistics hook", async () => {
  const { readFile } = await import("node:fs/promises");
  const home = await readFile(new URL("../src/pages/HomePage.jsx", import.meta.url), "utf8");
  assert.ok(home.includes('<PlatformDataPanel profile={user} compact />'));
  assert.ok(!home.includes('Boolean(user?.codeforcesHandle)'));
  const binding = await readFile(new URL("../src/pages/AccountBindingPage.jsx", import.meta.url), "utf8");
  assert.ok(binding.includes("<PlatformStatistics")); assert.ok(binding.includes("usePlatformOverviews"));
  const hook = await readFile(new URL("../src/hooks/usePlatformOverviews.js", import.meta.url), "utf8");
  assert.ok(hook.includes("polls++ < 12")); assert.ok(hook.includes("controller.abort()"));
  assert.ok(hook.includes("window.clearTimeout(timer)"));
});
