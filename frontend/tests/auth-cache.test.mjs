import {test, beforeEach, afterEach} from "node:test";
import assert from "node:assert/strict";
import {getStoredAuth,clearStoredAuth,setStoredAuth} from "../src/auth.js";

const originalWindow=globalThis.window;
let storage;
beforeEach(()=>{
  storage=new Map();globalThis.window={localStorage:{getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)}};
  clearStoredAuth();
});
afterEach(()=>{clearStoredAuth();globalThis.window=originalWindow;});
const auth=()=>({user:{id:7,avatarUrl:"data:image/webp;base64,"+"x".repeat(200_000)},tokens:{accessToken:"old",refreshToken:"refresh"}});
test("one hundred auth reads parse the avatar-bearing payload only once",()=>{
  setStoredAuth(auth());
  const originalParse=JSON.parse;let parses=0;
  JSON.parse=(...args)=>{parses++;return originalParse(...args);};
  try{for(let n=0;n<100;n++)assert.equal(getStoredAuth().tokens.accessToken,"old");assert.equal(parses,1);}
  finally{JSON.parse=originalParse;}
});
test("returned auth cannot mutate cached user or tokens",()=>{
  setStoredAuth(auth());const read=getStoredAuth();read.tokens.accessToken="tampered";read.user.id=99;
  assert.equal(getStoredAuth().tokens.accessToken,"old");assert.equal(getStoredAuth().user.id,7);
});
test("cross-tab storage changes, corruption and logout are observed immediately",()=>{
  setStoredAuth(auth());assert.equal(getStoredAuth().user.id,7);
  storage.set("whut-training-auth",JSON.stringify({user:{id:8},tokens:{accessToken:"new"}}));
  assert.equal(getStoredAuth().user.id,8);
  storage.set("whut-training-auth","{broken");assert.equal(getStoredAuth(),null);
  assert.equal(storage.has("whut-training-auth"),false);
  setStoredAuth(auth());getStoredAuth();clearStoredAuth();assert.equal(getStoredAuth(),null);
});
