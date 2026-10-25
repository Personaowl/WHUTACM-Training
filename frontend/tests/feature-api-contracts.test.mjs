import assert from "node:assert/strict";
import { beforeEach, afterEach, test } from "node:test";
import http from "../src/api/http.js";
import { setStoredAuth, clearStoredAuth } from "../src/auth.js";
import * as binding from "../src/api/accountBinding.js";
import * as practice from "../src/api/practice.js";
import * as library from "../src/api/problemList.js";
import { getCodeforcesOverview, refreshCodeforcesOverview } from "../src/api/platformOverview.js";

const originalAdapter = http.defaults.adapter, originalWindow = globalThis.window;
let received;
beforeEach(() => {
  const storage = new Map();
  globalThis.window = { localStorage: { getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } };
  setStoredAuth({ user: { id: 7 }, tokens: { accessToken: "access", refreshToken: "refresh" } });
  http.defaults.adapter = async config => {
    received = config;
    return { data: { code: 200, data: { marker: "unchanged" } }, status: 200, headers: {}, config };
  };
});
afterEach(() => { clearStoredAuth(); http.defaults.adapter = originalAdapter; globalThis.window = originalWindow; });

for (const [name, invoke, method, url, body] of [
  ["start CF proof", () => binding.startCodeforcesBinding(7, "tourist"), "post", "/api/users/7/codeforces-binding/start", { handle: "tourist" }],
  ["finish CF proof", () => binding.finishCodeforcesBinding(7), "post", "/api/users/7/codeforces-binding/finish", {}],
  ["start AtCoder proof", () => binding.startAtCoderBinding(7, "owl"), "post", "/api/users/7/atcoder-binding/start", { handle: "owl" }],
  ["finish AtCoder proof", () => binding.finishAtCoderBinding(7), "post", "/api/users/7/atcoder-binding/finish", {}],
  ["read extra binding", () => binding.getAdditionalBinding(7, "luogu"), "get", "/api/users/7/platform-bindings/luogu"],
  ["start extra proof", () => binding.startAdditionalBinding(7, "nowcoder", "123"), "post", "/api/users/7/platform-bindings/nowcoder/start", { uid: "123" }],
  ["finish extra proof", () => binding.finishAdditionalBinding(7, "luogu"), "post", "/api/users/7/platform-bindings/luogu/finish", {}],
  ["practice draw", () => practice.drawPracticeProblem("800", "3500", ["dp"]), "post", "/api/practice/draw", { minRating: 800, maxRating: 3500, tags: ["dp"] }],
  ["practice tags", () => practice.getPracticeTags(), "get", "/api/practice/tags"],
  ["practice history", () => practice.getPracticeHistory(30), "get", "/api/practice/history"],
  ["delete practice draw", () => practice.deletePracticeDraw(12), "delete", "/api/practice/12"],
  ["CF overview", () => getCodeforcesOverview(7), "get", "/api/users/7/codeforces-overview"],
  ["refresh CF overview", () => refreshCodeforcesOverview(7), "post", "/api/users/7/codeforces-overview/refresh", {}],
  ["list collections", () => library.listProblemLists(), "get", "/api/problem-lists"],
  ["collection details", () => library.getProblemList(3), "get", "/api/problem-lists/3"],
  ["create collection", () => library.createProblemList({ name: "DP", shared: false }), "post", "/api/problem-lists", { name: "DP", shared: false }],
  ["edit collection", () => library.updateProblemList(3, { name: "DP", shared: true }), "patch", "/api/problem-lists/3", { name: "DP", shared: true }],
  ["delete collection", () => library.deleteProblemList(3), "delete", "/api/problem-lists/3"],
  ["add collection item", () => library.addProblemListItem(3, { link: "https://example.org/problem", rating: null }), "post", "/api/problem-lists/3/items", { link: "https://example.org/problem", rating: null }],
  ["edit collection item", () => library.updateProblemListItem(3, 12, { title: "New title", rating: 1200 }), "patch", "/api/problem-lists/3/items/12", { title: "New title", rating: 1200 }],
  ["delete collection item", () => library.deleteProblemListItem(3, 12), "delete", "/api/problem-lists/3/items/12"],
]) {
  test(`feature split preserves ${name} request and response`, async () => {
    assert.deepEqual(await invoke(), { code: 200, data: { marker: "unchanged" } });
    assert.equal(received.method, method); assert.equal(received.url, url);
    assert.equal(received.headers.Authorization, "Bearer access");
    assert.equal(received.headers["X-Refresh-Token"], "refresh");
    if (body !== undefined) assert.deepEqual(JSON.parse(received.data), body);
    if (name === "practice history") assert.deepEqual(received.params, { limit: 30 });
    if (name === "refresh CF overview") assert.equal(received.timeout, 45000);
  });
}

test("binding business failures keep their original response envelope", async () => {
  http.defaults.adapter = async config => ({ status: 400, data: { code: 400, message: "proof missing", data: null }, headers: {}, config });
  assert.deepEqual(await binding.finishAdditionalBinding(7, "nowcoder"), { code: 400, message: "proof missing", data: null });
});
