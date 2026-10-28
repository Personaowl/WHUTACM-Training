import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { platformSnapshotModel, platformProfileFacts } from "../src/utils/platformSnapshot.js";

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
  assert.equal(atcoder.rank,null); assert.equal(atcoder.rankRange,"1200–1599");
  assert.equal(nowcoder.rank,null); assert.equal(nowcoder.rankRange,"1100–1499");
  assert.notEqual(atcoder.accent,nowcoder.accent);
});

test("public facts keep zero and negative contribution, format dates in Beijing and hide unsupported fields",()=>{
  const facts=platformProfileFacts("codeforces",{fields:{friendOfCount:"0",contribution:"-8",ratedContestCount:"62",country:"China",city:"Wuhan",organization:"WHUT",registrationTimeSeconds:"1696851033",email:"private@example.test",color:"purple"}});
  assert.equal(facts.find(item=>item.key==="friendOfCount").value,"0");
  assert.equal(facts.find(item=>item.key==="contribution").value,"-8");
  assert.equal(facts.find(item=>item.key==="location").value,"Wuhan, China");
  assert.match(facts.find(item=>item.key==="registrationTimeSeconds").value,/2023\/10\/09/);
  assert.ok(!facts.some(item=>["email","color"].includes(item.key)));
  for(const raw of [null,{}, {fields:[]},{fields:{contribution:"NaN",friendOfCount:-1,lastOnlineTimeSeconds:"0"}}])assert.deepEqual(platformProfileFacts("codeforces",raw),[]);
});

test("public facts are platform-specific and bind to the verified account snapshot",()=>{
  const fields={organization:"WHUT-ACM-81967F42",country:"China",globalRank:"9671",ratedContestCount:"13",followerCount:"25",followingCount:"17",attemptedCount:"1366",registrationTimeSeconds:"1597576705"};
  const model=platformSnapshotModel("luogu","123",overview("luogu",{profileInfo:{fields}}));
  assert.deepEqual(model.facts.map(item=>item.key),["followingCount","followerCount","attemptedCount","registrationTimeSeconds"]);
  assert.equal(model.highlights.length,3);
  assert.deepEqual(platformSnapshotModel("luogu","new",overview("luogu",{profileInfo:{fields}})).facts,[]);
  const atcoder=platformProfileFacts("atcoder",{fields});
  assert.ok(!atcoder.some(item=>item.key==="organization"));
  assert.equal(atcoder.find(item=>item.key==="globalRank").label,"算法排名");
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
