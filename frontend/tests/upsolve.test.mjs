import assert from "node:assert/strict";
import { test } from "node:test";
import { problemHost, requireUpsolveData, upsolveDate } from "../src/utils/upsolve.js";

test("upsolve platform labels match exact hosts, not malicious suffixes", () => {
  assert.equal(problemHost("https://codeforces.com/contest/123/problem/A"), "Codeforces");
  assert.equal(problemHost("https://www.luogu.com.cn/problem/P1001"), "洛谷");
  assert.equal(problemHost("https://ac.nowcoder.com/acm/problem/1"), "牛客");
  assert.equal(problemHost("https://evilcodeforces.com/a"), "evilcodeforces.com");
  assert.equal(problemHost("not a url"), "题目");
});
test("upsolve errors cannot be treated as saved data", () => {
  assert.deepEqual(requireUpsolveData({ code: 200, data: { items: [] } }), { items: [] });
  assert.throws(() => requireUpsolveData({ code: 409, message: "已存在" }), /已存在/);
  assert.throws(() => requireUpsolveData(null), /操作失败/);
});
test("missing and invalid completion times are not rendered as invalid dates", () => {
  assert.equal(upsolveDate(null), "");
  assert.equal(upsolveDate("bad"), "");
  assert.match(upsolveDate("2026-10-23T12:00:00Z"), /10.*23/);
});
