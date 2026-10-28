import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeRatingHistory, ratingHistoryModel, ratingContestUrl } from "../src/utils/ratingHistory.js";
const point=(id,time,rating)=>({contestId:id,ratingUpdateTimeSeconds:time,newRating:rating,contestName:`Contest ${id}`,url:`https://codeforces.com/contest/${id}`});
test("missing rating history is not confused with a measured empty history",()=>{
  assert.equal(normalizeRatingHistory(null,"codeforces"),null);
  assert.equal(normalizeRatingHistory(undefined,"codeforces"),null);
  assert.deepEqual(normalizeRatingHistory([],"codeforces"),[]);
  assert.equal(ratingHistoryModel([]),null);
});
test("rating history is chronological, keeps the latest duplicate and retains legitimate zero",()=>{
  const input=[point(2,200,1400),point(1,100,0),point(2,300,1500),point(3,400,NaN),point(4,0,1200),point(5,500,-1)];
  const result=normalizeRatingHistory(input,"codeforces");
  assert.deepEqual(result.map(row=>[row.contestId,row.newRating]),[["1",0],["2",1500]]);
  assert.equal(input.length,6);
});
test("new chart uses an explicit bounded history rather than a recent-eight fallback",()=>{
  const points=normalizeRatingHistory(Array.from({length:600},(_,i)=>point(i+1,1000+i,i+800)),"codeforces");
  assert.equal(points.length,500);assert.equal(points[0].contestId,"101");assert.equal(points.at(-1).contestId,"600");
});
test("single point and same-second histories have nonzero chart ranges",()=>{
  for(const points of [[point(1,1000,0)],[point(1,1000,1900),point(2,1000,2000)]]){
    const model=ratingHistoryModel(points);
    assert.ok(model.end>model.start);assert.ok(model.max>model.min);assert.ok(model.ticks.length<10);
    assert.ok(model.min<=Math.min(...points.map(p=>p.newRating)));
    assert.ok(model.max>=Math.max(...points.map(p=>p.newRating)));
  }
});
test("contest links must stay on each platform's HTTPS origin",()=>{
  assert.equal(ratingContestUrl("codeforces","javascript:alert(1)"),null);
  assert.equal(ratingContestUrl("atcoder","https://atcoder.jp.evil.com/contests/a"),null);
  assert.equal(ratingContestUrl("nowcoder","http://ac.nowcoder.com/acm/contest/1"),null);
  assert.equal(ratingContestUrl("codeforces","https://name@codeforces.com/contest/1"),null);
  assert.equal(ratingContestUrl("codeforces","https://codeforces.com:444/contest/1"),null);
  assert.equal(ratingContestUrl("atcoder","https://atcoder.jp/contests/abc100"),"https://atcoder.jp/contests/abc100");
});
