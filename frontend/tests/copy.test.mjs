import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = path => readFile(new URL(`../src/${path}`, import.meta.url), "utf8");
test("home shortcuts do not repeat explanatory slogans", async () => {
  const home = await source("pages/HomePage.jsx");
  for (const text of ["我的训练空间", "把值得一做的题目分享给训练伙伴", "按难度挑选下一题", "记录待补题目，逐题完成"]) assert.ok(!home.includes(text));
  assert.ok(home.includes('aria-label="题单入口"'));
});
test("shorter binding copy still explains limitations and verification safety", async () => {
  const binding = await source("components/profile/AdditionalPlatformBinding.jsx");
  assert.ok(binding.includes("比赛 Rating · 解题记录"));
  assert.ok(!binding.includes("暂不统计训练数据"));
  assert.ok(binding.includes("不要把验证码发到讨论、评论或题解中"));
  assert.ok(binding.includes("完整验证码并保存"));
});
test("contest timezone and practice tag matching rules remain explicit", async () => {
  assert.ok((await source("pages/ContestsPage.jsx")).includes("北京时间（UTC+8）"));
  assert.ok((await source("pages/PracticePage.jsx")).includes("题目需同时包含所选标签"));
});
