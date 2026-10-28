import { test } from "node:test";
import assert from "node:assert/strict";
import { ratingBand, ratingBands, ratingBandRange } from "../src/utils/ratingPalette.js";
import { difficultyColor } from "../src/utils/codeforcesCharts.js";

test("each platform owns its rating thresholds rather than borrowing CF colors", () => {
  assert.deepEqual(ratingBands("codeforces").map(item => item.min), [0,1200,1400,1600,1900,2100,2400]);
  assert.deepEqual(ratingBands("atcoder").map(item => item.min), [0,400,800,1200,1600,2000,2400,2800]);
  assert.deepEqual(ratingBands("nowcoder").map(item => item.min), [0,700,1100,1500,2000,2400,2800]);
  assert.equal(ratingBand("codeforces",1258).label,"绿色");
  assert.equal(ratingBand("atcoder",1258).label,"水色");
  assert.equal(ratingBand("nowcoder",1258).label,"蓝色");
});

test("every lower and upper boundary is inclusive/exclusive without holes", () => {
  for(const platform of ["codeforces","atcoder","nowcoder"]){
    const bands=ratingBands(platform);
    for(let i=0;i<bands.length;i++){
      const band=bands[i];
      assert.equal(ratingBand(platform,band.min),band);
      if(i){assert.equal(ratingBand(platform,band.min-1),bands[i-1]);assert.equal(bands[i-1].max,band.min);}
      if(Number.isFinite(band.max))assert.equal(ratingBand(platform,band.max-1),band);
    }
    assert.equal(ratingBand(platform,15000),bands.at(-1));
    assert.equal(ratingBand(platform,null),null);
    assert.equal(ratingBand(platform,-1),null);
    assert.equal(ratingBand(platform,NaN),null);
  }
});

test("official AtCoder and Nowcoder point colors preserve their own hues", () => {
  assert.deepEqual(ratingBands("atcoder").map(item=>item.color),["#808080","#804000","#008000","#00c0c0","#0000ff","#c0c000","#ff8000","#ff0000"]);
  assert.deepEqual(ratingBands("nowcoder").map(item=>item.color),["#b4b4b4","#c177e7","#5ea1f4","#25bb9b","#e4c200","#ff8800","#ff020a"]);
  assert.equal(ratingBand("codeforces",2100),ratingBand("codeforces",2399));
  assert.equal(ratingBand("codeforces",2600),ratingBand("codeforces",3000));
});

test("CF solved difficulty bars and rating bands share the same saturated palette", () => {
  for(let rating=800;rating<=3500;rating+=100)assert.equal(difficultyColor(rating),ratingBand("codeforces",rating).color);
  assert.equal(new Set(ratingBands("codeforces").map(item=>item.color)).size,7);
  assert.equal(ratingBandRange(ratingBand("atcoder",1258)),"1200–1599");
  assert.equal(ratingBandRange(ratingBand("nowcoder",2800)),"2800+");
});

test("rating text inks retain readable contrast on white even for yellow and cyan", () => {
  const luminance=hex=>{
    const linear=[1,3,5].map(index=>parseInt(hex.slice(index,index+2),16)/255).map(value=>value<=0.04045?value/12.92:((value+0.055)/1.055)**2.4);
    return linear[0]*0.2126+linear[1]*0.7152+linear[2]*0.0722;
  };
  for(const platform of ["codeforces","atcoder","nowcoder"])
    for(const band of ratingBands(platform))assert.ok(1.05/(luminance(band.ink)+0.05)>=4.5,`${platform} ${band.label} text contrast`);
});
