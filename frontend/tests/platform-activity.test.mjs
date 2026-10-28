import { test } from "node:test";
import assert from "node:assert/strict";
import { buildPlatformActivity } from "../src/utils/platformActivity.js";
const row = (platform, activityMetric, count) => ({platform,accountId:"same",activityMetric,activity:[{date:"2026-10-27",count}]});
test("activity intensity counts active platforms rather than adding incompatible quantities", () => {
  const rows=[row("codeforces","AC_SUBMISSIONS",2),row("atcoder","AC_SUBMISSIONS",3),row("luogu","PUBLIC_ACTIVITY",99),row("nowcoder","AC_SUBMISSIONS",4)];
  const result=buildPlatformActivity(rows);
  assert.equal(result.data[0].score,4);assert.equal(result.data[0].colorLevel,4);
  assert.equal(result.acCount,9);assert.equal(result.activeDays,1);
});
test("AC mode excludes Luogu activity and supports source filtering", () => {
  const rows=[row("codeforces","AC_SUBMISSIONS",2),row("luogu","PUBLIC_ACTIVITY",99)];
  assert.equal(buildPlatformActivity(rows,undefined,"ac").data[0].score,2);
  assert.equal(buildPlatformActivity(rows,["luogu"],"ac").available,0);
  assert.equal(buildPlatformActivity(rows,["luogu"]).data[0].score,1);
  assert.equal(buildPlatformActivity(rows,[]).available,0);
});
test("unavailable data is not confused with a measured empty history", () => {
  const result=buildPlatformActivity([{platform:"atcoder",accountId:"a",activity:null},
    {platform:"luogu",accountId:"b",activity:[]},{platform:"codeforces",accountId:null,activity:[]}]);
  assert.equal(result.available,1);assert.deepEqual(result.data,[]);
});
test("bad dates and nonpositive counts do not create activity", () => {
  const result=buildPlatformActivity([{platform:"codeforces",accountId:"a",activityMetric:"AC_SUBMISSIONS",activity:[
    {date:"bad",count:9},{date:"2026-10-27",count:-1},{date:"2026-10-28",count:0}]}]);
  assert.deepEqual(result.data,[]);
});
