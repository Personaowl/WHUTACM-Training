import {test} from "node:test";
import assert from "node:assert/strict";
import {checkInPollDelay,waitForCheckInPoll} from "../src/utils/checkInPolling.js";

test("fast first checks are retained while queued and hidden requests back off",()=>{
  assert.equal(checkInPollDelay(0),250);assert.equal(checkInPollDelay(3),500);
  assert.equal(checkInPollDelay(8),1000);assert.equal(checkInPollDelay(20),2000);
  assert.equal(checkInPollDelay(0,true),5000);
});
test("navigation cancellation rejects pending wait and clears its timer",async()=>{
  const controller=new AbortController();
  const waiting=waitForCheckInPoll(60_000,controller.signal);controller.abort();
  await assert.rejects(waiting,{name:"AbortError"});
  await assert.rejects(waitForCheckInPoll(60_000,controller.signal),{name:"AbortError"});
});
test("completed waits remove their abort listener",async()=>{
  const controller=new AbortController();let removed=0;
  const original=controller.signal.removeEventListener.bind(controller.signal);
  controller.signal.removeEventListener=(...args)=>{removed++;return original(...args);};
  await waitForCheckInPoll(1,controller.signal);assert.equal(removed,1);
});
