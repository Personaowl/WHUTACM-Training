import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { platformSnapshotModel } from "../src/utils/platformSnapshot.js";

const overview=(platform,fields={})=>({platform,accountId:"123",connected:true,dataStatus:"READY",...fields});

test("CF card reuses known statistics and the existing rank titles",()=>{
  const model=platformSnapshotModel("codeforces","123",overview("codeforces",{rating:1961,maxRating:2040,solvedCount:1748}));
  assert.equal(model.rank,"Candidate Master");
  assert.deepEqual(model.metrics.map(item=>[item.key,item.value]),[["rating",1961],["maxRating",2040],["solvedCount",1748]]);
  assert.equal(model.url,"https://codeforces.com/profile/123");
  assert.equal(model.source,"Codeforces API");
});

test("each card uses its own rating palette rather than CF titles",()=>{
  const atcoder=platformSnapshotModel("atcoder","123",overview("atcoder",{rating:1258}));
  const nowcoder=platformSnapshotModel("nowcoder","123",overview("nowcoder",{rating:1258}));
  assert.equal(atcoder.rank,"水色"); assert.equal(atcoder.rankRange,"1200–1599");
  assert.equal(nowcoder.rank,"蓝色"); assert.equal(nowcoder.rankRange,"1100–1499");
  assert.notEqual(atcoder.accent,nowcoder.accent);
});

test("Luogu only shows solved problems, even if a foreign rating field is present",()=>{
  const model=platformSnapshotModel("luogu","123",overview("luogu",{displayName:"猫头鹰",rating:1961,maxRating:2040,solvedCount:1349}));
  assert.equal(model.name,"猫头鹰"); assert.equal(model.showUid,true); assert.equal(model.rank,null);
  assert.deepEqual(model.metrics.map(item=>[item.key,item.value]),[["solvedCount",1349]]);
});

test("true zero survives, missing metrics do not become fake statistics",()=>{
  for(const platform of ["codeforces","atcoder","nowcoder"]){
    const model=platformSnapshotModel(platform,"123",overview(platform,{rating:0,maxRating:null,solvedCount:0}));
    assert.deepEqual(model.metrics.map(item=>item.value),[0,0]);
  }
  assert.deepEqual(platformSnapshotModel("nowcoder","123",overview("nowcoder")).metrics,[]);
  assert.deepEqual(platformSnapshotModel("nowcoder","123",overview("nowcoder",{rating:NaN,maxRating:Infinity,solvedCount:-1})).metrics,[]);
});

test("stale accounts, mismatched platforms and unverified rows never provide card statistics",()=>{
  for(const row of [overview("nowcoder",{accountId:"old",displayName:"Wrong",rating:2500}),overview("nowcoder",{connected:false,rating:2500}),overview("luogu",{displayName:"Wrong",rating:2500})]){
    const model=platformSnapshotModel("nowcoder","123",row);
    assert.equal(model.name,"UID 123");assert.deepEqual(model.metrics,[]);assert.equal(model.rank,null);
  }
  assert.equal(platformSnapshotModel("nowcoder",null,overview("nowcoder")),null);
  assert.equal(platformSnapshotModel("unknown","123",overview("nowcoder")),null);
});

test("nicknames affect display only, UID remains the link target and account identity",()=>{
  const model=platformSnapshotModel("nowcoder","123",overview("nowcoder",{displayName:"新的名字"}));
  assert.equal(model.name,"新的名字"); assert.equal(model.accountId,"123");
  assert.equal(model.url,"https://www.nowcoder.com/users/123");
  assert.equal(platformSnapshotModel("codeforces","a/b?x=1",null).url,"https://codeforces.com/profile/a%2Fb%3Fx%3D1");
});

test("cards are static presentations with local platform logos, not new OJ avatar requests",async()=>{
  const source=await readFile(new URL("../src/components/profile/PlatformSnapshotCard.jsx",import.meta.url),"utf8");
  const css=await readFile(new URL("../src/components/profile/PlatformSnapshotCard.css",import.meta.url),"utf8");
  assert.match(source,/src=\{platform.icon\}/);
  assert.doesNotMatch(source,/fetch\(|useEffect|setInterval|userpic|titlePhoto|dangerouslySetInnerHTML/);
  assert.doesNotMatch(css,/animation:|backdrop-filter:|will-change:/);
  assert.match(source,/rel="noopener noreferrer"/);
  assert.match(source,/role="status"/);
});
