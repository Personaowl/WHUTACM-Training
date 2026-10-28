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
  assert.equal(profilePlatformAccount("codeforces",{codeforcesHandle:null}, {codeforces:{connected:true,accountId:"verified"}}),"verified");
  assert.equal(profilePlatformAccount("atcoder",{atcoderHandle:"stale"}, {atcoder:{connected:true,accountId:"verified"}}),"verified");
  assert.equal(profilePlatformAccount("atcoder",{atcoderHandle:"stale"}, {atcoder:{connected:false,accountId:null}}),null);
  assert.equal(profilePlatformAccount("atcoder",{atcoderHandle:"fallback"}, {}),"fallback");
  assert.equal(profilePlatformAccount("atcoder",{}, {atcoder:{connected:true,accountId:"verified"}}),"verified");
  assert.equal(profilePlatformAccount("atcoder",{}, {atcoder:{connected:false,accountId:"pending"}}),null);
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
  assert.ok(source.includes('detailsOpen && <Suspense'));
  assert.ok(source.includes('lazy(() => import("./CodeforcesOverview"))'));
  assert.ok(source.includes('detailsOnly onUpdated={reload}'));
  assert.ok(!source.includes('fetch('));
});

test("account summary uses a shared snapshot card with equal-sized metrics", async () => {
  const component = await readFile(new URL("../src/components/profile/PlatformDataPanel.jsx",import.meta.url),"utf8");
  const css = await readFile(new URL("../src/components/profile/PlatformSnapshotCard.css",import.meta.url),"utf8");
  assert.ok(component.includes('className="profile-platform-summary"'));
  assert.ok(component.includes('<PlatformSnapshotCard platform={selected} accountId={account} overview={data} compact={compact}'));
  assert.match(css,/\.platform-snapshot-primary dd,\s*\.platform-snapshot-metrics dd\s*\{[^}]*font-size:\s*var\(--platform-number-size\)/);
  assert.match(css,/\.platform-snapshot-dossier\s*\{[^}]*border:\s*1px solid var\(--snapshot-line\)/);
  const profile = await readFile(new URL("../src/pages/ProfilePage.jsx",import.meta.url),"utf8");
  assert.ok(!profile.includes('Codeforces rating'));
  assert.ok(!profile.includes('`Codeforces: ${currentProfile.codeforcesHandle}'));
});

test("home compact panel preserves the selected platform in its detail link and omits charts", async () => {
  const source = await readFile(new URL("../src/components/profile/PlatformDataPanel.jsx",import.meta.url),"utf8");
  assert.ok(source.includes('to={`/profile?platform=${selectedKey}`}'));
  assert.ok(source.includes('!compact && account'));
  assert.ok(source.includes('item.key === searchParams.get("platform")'));
  const bindingCss = await readFile(new URL("../src/components/profile/PlatformStatistics.css",import.meta.url),"utf8");
  assert.ok(bindingCss.includes('font-size: var(--platform-number-size)'));
});

test("unbound account guidance is one centered group with a centered 44px action", async () => {
  const component = await readFile(new URL("../src/components/profile/PlatformDataPanel.jsx",import.meta.url),"utf8");
  const css = await readFile(new URL("../src/components/profile/PlatformDataPanel.css",import.meta.url),"utf8");
  assert.ok(component.includes('data-unbound={!loading && !account || undefined}'));
  assert.match(css, /\.profile-platform-summary\[data-unbound="true"\]\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\)[^}]*text-align:\s*center/);
  assert.match(css, /\.profile-platform-empty > :is\(\.button-primary, \.button-ghost\)\s*\{[^}]*display:\s*inline-flex[^}]*align-items:\s*center[^}]*justify-content:\s*center[^}]*min-height:\s*44px/);
});
