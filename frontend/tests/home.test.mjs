import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import axios from "axios";
import http from "../src/api/http.js";
import { getHomeOverview } from "../src/api/home.js";
import { setStoredAuth } from "../src/auth.js";
import { getCurrentUserPoints } from "../src/utils/home.js";

const originalAdapter = http.defaults.adapter;
const originalWindow = globalThis.window;

beforeEach(() => {
  const storage = new Map();
  globalThis.window = {
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    },
  };
  setStoredAuth({ user: { id: 7 }, tokens: { accessToken: "test-access", refreshToken: "test-refresh" } });
});

afterEach(() => {
  http.defaults.adapter = originalAdapter;
  globalThis.window = originalWindow;
});

test("the home overview retains its direct response and requested leaderboard limit", async () => {
  const overview = { totalUsers: 0, todayProblem: [], topUsers: [], dailySubmissionSummary: { todaySubmissions: 0 } };
  http.defaults.adapter = async (config) => {
    assert.equal(config.url, "/api/home");
    assert.equal(config.params.top, 10);
    assert.equal(config.headers.Authorization, "Bearer test-access");
    return { status: 200, data: overview, config, headers: {} };
  };
  assert.deepEqual(await getHomeOverview(), overview);
});

test("server failures are not rendered as a successful empty home overview", async () => {
  http.defaults.adapter = async (config) => {
    throw new axios.AxiosError("unavailable", "ERR_BAD_RESPONSE", config, null,
      { status: 503, data: { code: 503, message: "unavailable" }, config, headers: {} });
  };
  await assert.rejects(getHomeOverview(), /加载首页数据失败/);
});

test("malformed or application-error overview responses are rejected", async () => {
  for (const data of [null, "unavailable", [], { code: 500, message: "failed" }]) {
    http.defaults.adapter = async (config) => ({ status: 200, data, config, headers: {} });
    await assert.rejects(getHomeOverview(), /加载首页数据失败/);
  }
});

test("home points come from the authenticated response, not an absent or stale login snapshot", () => {
  assert.equal(getCurrentUserPoints({ currentUserId: 7, currentUserPoints: 12400 }, 7), 12400);
  assert.equal(getCurrentUserPoints({ currentUserId: 7, currentUserPoints: 1900 }, 7), 1900);
  assert.equal(getCurrentUserPoints({ currentUserId: 7, currentUserPoints: 0 }, 7), 0);
});

test("pending, unavailable and cross-account points never masquerade as zero", () => {
  assert.equal(getCurrentUserPoints(null, 7), null);
  assert.equal(getCurrentUserPoints({}, 7), null);
  assert.equal(getCurrentUserPoints({ currentUserId: 7, currentUserPoints: null }, 7), null);
  assert.equal(getCurrentUserPoints({ currentUserId: 8, currentUserPoints: 12400 }, 7), null);
  assert.equal(getCurrentUserPoints({ currentUserId: 7, currentUserPoints: "invalid" }, 7), null);
});
