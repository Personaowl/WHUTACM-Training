import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { platformSnapshotModel, platformProfileFacts, platformStatisticRows } from "../src/utils/platformSnapshot.js";

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

test("dossier layout groups every available fact once without losing zero or empty-source states",()=>{
  const sources={
    codeforces:{organization:"WHUT",city:"Wuhan",country:"China",realName:"Public Name",friendOfCount:"0",contribution:"-8",ratedContestCount:"62",registrationTimeSeconds:"1696851033",lastOnlineTimeSeconds:"1793160000",attemptedCount:"1800",acceptedSubmissionCount:"2401"},
    atcoder:{organization:"WHUT",country:"China",globalRank:"9671",ratedContestCount:"13",lastCompetedDate:"2026-03-28"},
    luogu:{followingCount:"0",followerCount:"25",attemptedCount:"1366",registrationTimeSeconds:"1597576705"},
    nowcoder:{globalRank:"3916",followingCount:"13",followerCount:"6",attemptedCount:"964",submissionCount:"1543"},
  };
  for(const [platform,fields] of Object.entries(sources)){
    const model=platformSnapshotModel(platform,"123",overview(platform,{profileInfo:{fields}}));
    const grouped=Object.values(model.factGroups).flat();
    assert.equal(new Set(grouped.map(item=>item.key)).size,model.facts.length);
    assert.deepEqual(grouped.map(item=>item.key).sort(),model.facts.map(item=>item.key).sort());
    assert.ok(model.factGroups.statistics.every(item=>item.kind==="number"));
    assert.ok(model.factGroups.records.every(item=>!["organization","location","country"].includes(item.key)));
    assert.deepEqual(platformSnapshotModel(platform,"new",overview(platform,{profileInfo:{fields}})).factGroups,{identity:[],statistics:[],records:[]});
  }
  const cf=platformSnapshotModel("codeforces","123",overview("codeforces",{profileInfo:{fields:sources.codeforces}}));
  assert.deepEqual(cf.factGroups.identity.map(item=>item.key),["organization","location"]);
  assert.deepEqual(cf.factGroups.records.map(item=>item.key),["realName","registrationTimeSeconds","lastOnlineTimeSeconds"]);
  assert.equal(cf.factGroups.statistics.find(item=>item.key==="friendOfCount").value,"0");
  const nc=platformSnapshotModel("nowcoder","123",overview("nowcoder",{profileInfo:{fields:sources.nowcoder}}));
  assert.deepEqual(nc.factGroups.records.map(item=>item.key),["globalRank","followingCount","followerCount"]);
  assert.deepEqual(nc.factGroups.statistics.map(item=>item.key),["attemptedCount","submissionCount"]);
});

test("Luogu only shows solved problems, even if a foreign rating field is present",()=>{
  const model=platformSnapshotModel("luogu","123",overview("luogu",{displayName:"猫头鹰",rating:1961,maxRating:2040,solvedCount:1349}));
  assert.equal(model.name,"猫头鹰"); assert.equal(model.showUid,true); assert.equal(model.rank,null);
  assert.deepEqual(model.metrics.map(item=>[item.key,item.value]),[["solvedCount",1349]]);
});

test("secondary statistic rows separate practice samples and divide only the available fields",()=>{
  const facts=platformProfileFacts("codeforces",{fields:{ratedContestCount:"62",friendOfCount:"0",contribution:"-8",attemptedCount:"1800",acceptedSubmissionCount:"2401"}});
  const rows=platformStatisticRows(facts);
  assert.deepEqual(rows.map(row=>row.facts.map(fact=>fact.key)),[["ratedContestCount","friendOfCount","contribution"],["attemptedCount","acceptedSubmissionCount"]]);
  assert.equal(rows[0].facts[1].value,"0");
  assert.equal(rows[0].facts[2].value,"-8");
  for(const subset of [[],facts.slice(0,1),facts.slice(3),facts.filter(fact=>fact.key!=="friendOfCount")]){
    const shown=platformStatisticRows(subset);
    assert.ok(shown.every(row=>row.facts.length>0));
    assert.deepEqual(shown.flatMap(row=>row.facts),subset);
  }
  assert.deepEqual(platformStatisticRows(),[]);
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
