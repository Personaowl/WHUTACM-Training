import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { PROFILE_PLATFORMS, availablePlatformMetrics, nextPlatformTab, platformProfileUrl, profilePlatformAccount } from "../src/utils/platformPanel.js";

test("profile offers exactly four platforms with locally hosted official icons", () => {
  assert.deepEqual(PROFILE_PLATFORMS.map(item => item.key), ["codeforces", "atcoder", "luogu", "nowcoder"]);
  assert.ok(PROFILE_PLATFORMS.every(item => item.icon.startsWith("/platforms/")));
});
test("only known metrics are shown; zero is real and missing highest is not an empty tile", () => {
  assert.deepEqual(availablePlatformMetrics("luogu", {rating:null,maxRating:null,solvedCount:0}).map(item => [item.key,item.value]), [["solvedCount",0]]);
  assert.deepEqual(availablePlatformMetrics("nowcoder", {rating:1644,maxRating:null,solvedCount:410}).map(item => item.key), ["rating","solvedCount"]);
  assert.equal(availablePlatformMetrics("atcoder", {rating:1600})[0].label,"算法 Rating");
  assert.equal(availablePlatformMetrics("luogu", {rating:1600})[0].label,"比赛 Rating");
  for (const value of [null,undefined,NaN,Infinity,-1,"100"]) assert.deepEqual(availablePlatformMetrics("codeforces",{rating:value}),[]);
});
test("pending and old account metadata cannot masquerade as verified bindings", () => {
  assert.equal(profilePlatformAccount("luogu",{}, {luogu:{connected:false,accountId:"123",pendingAccountId:"456"}}),null);
  assert.equal(profilePlatformAccount("luogu",{}, {luogu:{connected:true,accountId:123}}),"123");
  assert.equal(profilePlatformAccount("codeforces",{}, {codeforces:{connected:true,accountId:"old"}}),null);
  assert.equal(profilePlatformAccount("atcoder",{atcoderHandle:"new"}, {atcoder:{connected:true,accountId:"old"}}),"new");
});
test("profile links use fixed platform origins and encoded accounts", () => {
  assert.equal(platformProfileUrl("unknown","1"),null);
  assert.equal(platformProfileUrl("luogu",null),null);
  assert.equal(platformProfileUrl("nowcoder","123"),"https://www.nowcoder.com/users/123");
  assert.equal(platformProfileUrl("codeforces","a/b?x=1"),"https://codeforces.com/profile/a%2Fb%3Fx%3D1");
});
test("tab keyboard navigation wraps and supports Home and End", () => {
  assert.equal(nextPlatformTab(3,"ArrowRight"),0);
  assert.equal(nextPlatformTab(0,"ArrowLeft"),3);
  assert.equal(nextPlatformTab(2,"Home"),0);
  assert.equal(nextPlatformTab(1,"End"),3);
  assert.equal(nextPlatformTab(0,"Tab"),null);
});
test("detail request and charts are mounted only after the user expands CF details", async () => {
  const source = await readFile(new URL("../src/components/profile/PlatformDataPanel.jsx",import.meta.url),"utf8");
  assert.ok(source.includes('role="tablist"')); assert.ok(source.includes('role="tab"')); assert.ok(source.includes('role="tabpanel"'));
  assert.ok(source.includes('aria-selected={selectedKey === platform.key}'));
  assert.ok(source.includes('detailsOpen && <CodeforcesOverview'));
  assert.ok(source.includes('detailsOnly onUpdated={reload}'));
  assert.ok(!source.includes('fetch('));
});

test("account and current rating lead the summary without repeated platform logos", async () => {
  const component = await readFile(new URL("../src/components/profile/PlatformDataPanel.jsx",import.meta.url),"utf8");
  const css = await readFile(new URL("../src/components/profile/PlatformDataPanel.css",import.meta.url),"utf8");
  assert.ok(component.includes('className="profile-platform-summary"'));
  assert.ok(component.includes('index === 0 ? " is-primary"'));
  assert.ok(component.includes('`UID ${account}`'));
  assert.ok(!component.includes('src={selected.icon}'));
  assert.ok(css.includes('.profile-platform-metrics .is-primary dd { font-size: 42px; }'));
  const profile = await readFile(new URL("../src/pages/ProfilePage.jsx",import.meta.url),"utf8");
  assert.ok(!profile.includes('Codeforces rating'));
  assert.ok(!profile.includes('`Codeforces: ${currentProfile.codeforcesHandle}'));
});
