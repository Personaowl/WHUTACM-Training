import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import axios from "axios";
import http from "../src/api/http.js";
import { checkInToday } from "../src/api/dailyProblem.js";
import { clearStoredAuth, setStoredAuth } from "../src/auth.js";

const originalAdapter = http.defaults.adapter;
const originalWindow = globalThis.window;
const originalDocument = globalThis.document;

beforeEach(() => {
  const storage = new Map();
  globalThis.window = {
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    },
  };
  globalThis.document = { visibilityState: "visible" };
  setStoredAuth({ user: { id: 7 }, tokens: { accessToken: "access", refreshToken: "refresh" } });
});

afterEach(() => {
  http.defaults.adapter = originalAdapter;
  clearStoredAuth();
  globalThis.window = originalWindow;
  globalThis.document = originalDocument;
});

function response(config, data) {
  return { config, status: 200, headers: {}, statusText: "200", data: { code: 200, data } };
}

function immediateTimers(t) {
  const delays = [];
  t.mock.method(globalThis, "setTimeout", (callback, delay) => {
    delays.push(delay);
    queueMicrotask(callback);
    return 0;
  });
  return delays;
}

test("check-in returns the server result and stops polling after success", async (t) => {
  const delays = immediateTimers(t);
  const controller = new AbortController();
  let polls = 0;
  http.defaults.adapter = async (config) => {
    assert.equal(config.signal, controller.signal);
    if (config.method === "post") return response(config, { jobId: "job/1", status: "PENDING" });
    assert.equal(config.url, "/api/daily-problem/check-in/job%2F1");
    polls += 1;
    return response(config, polls === 3
      ? { jobId: "job/1", status: "SUCCEEDED", result: { accepted: true, score: 1900 } }
      : { jobId: "job/1", status: "RUNNING" });
  };
  const result = await checkInToday(123, { signal: controller.signal });
  assert.equal(result.data.score, 1900);
  assert.equal(polls, 3);
  assert.deepEqual(delays, [250, 500, 500]);
});

test("cancelling a check-in stops subsequent status requests", async (t) => {
  immediateTimers(t);
  const controller = new AbortController();
  let polls = 0;
  http.defaults.adapter = async (config) => {
    if (config.method === "post") return response(config, { jobId: "job1", status: "PENDING" });
    polls += 1;
    controller.abort();
    return response(config, { jobId: "job1", status: "RUNNING" });
  };
  await assert.rejects(checkInToday(123, { signal: controller.signal }), axios.isCancel);
  assert.equal(polls, 1);
});

test("the final permitted status request can still complete successfully", async (t) => {
  immediateTimers(t);
  let polls = 0;
  http.defaults.adapter = async (config) => {
    if (config.method === "post") return response(config, { jobId: "job1", status: "PENDING" });
    polls += 1;
    return response(config, polls === 60
      ? { jobId: "job1", status: "SUCCEEDED", result: { accepted: true, score: 1200 } }
      : { jobId: "job1", status: "RUNNING" });
  };
  assert.equal((await checkInToday(123)).data.score, 1200);
  assert.equal(polls, 60);
});

test("a failed job does not schedule status requests", async (t) => {
  const delays = immediateTimers(t);
  let requests = 0;
  http.defaults.adapter = async (config) => {
    requests += 1;
    return response(config, { jobId: "job1", status: "FAILED", errorCode: 400, message: "not accepted" });
  };
  assert.equal((await checkInToday(123)).message, "not accepted");
  assert.equal(requests, 1);
  assert.deepEqual(delays, []);
});
