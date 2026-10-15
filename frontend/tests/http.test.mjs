import assert from "node:assert/strict";
import { beforeEach, afterEach, test } from "node:test";
import axios from "axios";
import http from "../src/api/http.js";
import { getStoredAuth, setStoredAuth } from "../src/auth.js";

const originalAdapter = axios.defaults.adapter;
const originalHttpAdapter = http.defaults.adapter;
const originalWindow = globalThis.window;

function respond(config, status, data) {
  return { config, status, data, headers: {}, statusText: String(status) };
}

function fail(config, status) {
  throw new axios.AxiosError("request failed", "ERR_BAD_RESPONSE", config, null,
    respond(config, status, { code: status, message: "request failed" }));
}

function installAdapter(adapter) {
  axios.defaults.adapter = adapter;
  http.defaults.adapter = adapter;
}

beforeEach(() => {
  const storage = new Map();
  const browser = new EventTarget();
  browser.localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  };
  globalThis.window = browser;
  setStoredAuth({ user: { id: 7, displayName: "member" },
    tokens: { accessToken: "old-access", refreshToken: "old-refresh" } });
});

afterEach(() => {
  axios.defaults.adapter = originalAdapter;
  http.defaults.adapter = originalHttpAdapter;
  globalThis.window = originalWindow;
});

function protectedRequest() {
  return http.get("/api/home", { headers: { Authorization: "Bearer old-access" } });
}

test("parallel 401 responses share one refresh and publish the latest token to the UI", async () => {
  let refreshCount = 0;
  let published;
  window.addEventListener("auth:tokens", (event) => { published = event.detail; });
  installAdapter(async (config) => {
    if (config.url === "/api/auth/refresh") {
      refreshCount += 1;
      assert.equal(config.timeout, 10000);
      return respond(config, 200, { code: 200,
        data: { accessToken: "new-access", refreshToken: "new-refresh" } });
    }
    if (config.headers.Authorization === "Bearer old-access") fail(config, 401);
    assert.equal(config.headers.Authorization, "Bearer new-access");
    return respond(config, 200, { code: 200 });
  });
  const responses = await Promise.all(Array.from({ length: 8 }, protectedRequest));
  assert.ok(responses.every((response) => response.status === 200));
  assert.equal(refreshCount, 1);
  assert.equal(getStoredAuth().tokens.refreshToken, "new-refresh");
  assert.equal(published.accessToken, "new-access");
});

test("a refresh service outage preserves the session", async () => {
  installAdapter(async (config) => fail(config, config.url === "/api/auth/refresh" ? 503 : 401));
  assert.equal((await protectedRequest()).status, 503);
  assert.equal(getStoredAuth().tokens.refreshToken, "old-refresh");
});

test("a refresh timeout preserves the session", async () => {
  installAdapter(async (config) => {
    if (config.url === "/api/auth/refresh") {
      throw new axios.AxiosError("timeout", "ECONNABORTED", config);
    }
    fail(config, 401);
  });
  await assert.rejects(protectedRequest(), /timeout/);
  assert.equal(getStoredAuth().tokens.refreshToken, "old-refresh");
});

test("an invalid refresh token signs the user out", async () => {
  let invalidations = 0;
  window.addEventListener("auth:invalid", () => { invalidations += 1; });
  installAdapter(async (config) => fail(config, 401));
  assert.equal((await protectedRequest()).status, 401);
  assert.equal(getStoredAuth(), null);
  assert.equal(invalidations, 1);
});

test("requests created with an old header use the token already stored by a completed refresh", async () => {
  setStoredAuth({ user: { id: 7 }, tokens: { accessToken: "latest", refreshToken: "latest-refresh" } });
  installAdapter(async (config) => {
    assert.equal(config.url, "/api/home");
    assert.equal(config.headers.Authorization, "Bearer latest");
    return respond(config, 200, { code: 200 });
  });
  assert.equal((await protectedRequest()).status, 200);
});

test("a late refresh cannot overwrite a new login", async () => {
  installAdapter(async (config) => {
    if (config.url !== "/api/auth/refresh") fail(config, 401);
    setStoredAuth({ user: { id: 8 }, tokens: { accessToken: "other-access", refreshToken: "other-refresh" } });
    return respond(config, 200, { code: 200,
      data: { accessToken: "late-access", refreshToken: "late-refresh" } });
  });
  await assert.rejects(protectedRequest(), /登录状态已改变/);
  assert.equal(getStoredAuth().user.id, 8);
  assert.equal(getStoredAuth().tokens.accessToken, "other-access");
});
