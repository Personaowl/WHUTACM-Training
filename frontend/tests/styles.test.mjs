import assert from "node:assert/strict";
import { before, test } from "node:test";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { build } from "vite";

let css;

before(async () => {
  const result = await build({
    root: fileURLToPath(new URL("../", import.meta.url)),
    logLevel: "silent",
    build: { write: false, minify: false, cssMinify: false },
  });
  css = result.output
    .filter((file) => file.type === "asset" && file.fileName.endsWith(".css"))
    .map((file) => file.source)
    .join("\n");
});

test("the production stylesheet keeps the existing ink-blue theme and rounded cards", () => {
  assert.match(css, /\.text-text-primary\s*\{\s*color:\s*#132238;/);
  assert.match(css, /\.bg-bg-secondary\s*\{\s*background-color:\s*#f4f4f0;/);
  assert.match(css, /\.rounded-ui\s*\{\s*border-radius:\s*16px;/);
  assert.match(css, /\.shadow-xs\s*\{[^}]*0 1px 2px 0/);
  assert.ok(css.includes(".shrink-0 {"));
});

test("conditional form fields and portals retain v3-compatible sibling spacing", () => {
  const rule = css.match(/\.stack-y-5\s*>\s*:not\(\[hidden\]\)\s*~\s*:not\(\[hidden\]\)\s*\{([^}]+)\}/);
  assert.ok(rule, "spacing must be applied between visible siblings, not to every non-last child");
  assert.match(rule[1], /margin-top:\s*calc\(5 \* \.25rem \* \(1 - var\(--tw-space-y-reverse\)\)\)/);
  assert.match(rule[1], /margin-bottom:/);
  assert.match(css, /textarea\s*\{\s*background-color:\s*(?:#fff|white);/);
});

test("sidebar scrollbars remain slim, themed and keyboard-scrollable", async () => {
  assert.match(css, /\.sidebar-nav\s*\{[^}]*scrollbar-width:\s*thin/s);
  assert.match(css, /scrollbar-color:\s*#63788f\s+transparent/);
  assert.match(css, /\.sidebar-nav::-webkit-scrollbar-button\s*\{[^}]*display:\s*none/s);
  const sidebar = await readFile(new URL("../src/components/ui/Sidebar.jsx", import.meta.url), "utf8");
  assert.match(sidebar, /className="sidebar-nav"[^>]*tabIndex=\{0\}/);
});

test("the shared refinement stylesheet is loaded after the base stylesheet", async () => {
  const entry = await readFile(new URL("../src/main.jsx", import.meta.url), "utf8");
  assert.ok(entry.indexOf('"./Refinements.css"') > entry.indexOf('"./index.css"'));
});

test("desktop typography has shared readable roles without zooming the application", async () => {
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  assert.match(refinements, /@media \(min-width: 1025px\)[\s\S]*--ui-body: 16px;[\s\S]*--ui-meta: 14px;/);
  assert.match(refinements, /body\s*\{[^}]*font-size:\s*var\(--ui-body\)/);
  assert.match(refinements, /\.app-content \.text-sm\s*\{[^}]*font-size:\s*var\(--ui-body\)/);
  assert.match(refinements, /\.app-content \.text-xs\s*\{[^}]*font-size:\s*var\(--ui-meta\)/);
  assert.match(refinements, /\.auth-field\s*\{[^}]*height:\s*52px;[^}]*font-size:\s*17px;/);
  assert.doesNotMatch(refinements, /(?:^|[;{])\s*zoom\s*:/m);
  assert.doesNotMatch(refinements, /transform:\s*scale\(/);
});

test("the auth cover describes daily problems, personal training and analysis in Chinese", async () => {
  const frame = await readFile(new URL("../src/components/ui/AuthFrame.jsx", import.meta.url), "utf8");
  assert.match(frame, /auth-training-modes/);
  for (const label of ["每日一题", "个人训练", "个人分析", "每日选题 · 提交校验", "自主练习 · 补题计划", "训练记录 · 数据统计"]) {
    assert.ok(frame.includes(label), `missing auth cover copy: ${label}`);
  }
  assert.doesNotMatch(frame, /<small>[^<]*(?:Easy|Hard|AtCoder ABC)/);
  assert.doesNotMatch(frame, /geometry-ring|auth-seam-node/);
});

test("the pale theme retains readable ink and a single subtle overview gradient", async () => {
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  const home = await readFile(new URL("../src/pages/HomePage.css", import.meta.url), "utf8");
  assert.match(refinements, /--canvas:\s*#f4f7fb/);
  assert.match(refinements, /--brand-soft:\s*#f3f8fd/);
  assert.match(home, /\.home-page \.home-profile-panel\s*\{[^}]*color:\s*var\(--ink\)/s);
  assert.equal((home.match(/linear-gradient\(/g) || []).length, 1);
  assert.match(home, /\.home-page \.home-profile-panel a:focus-visible\s*\{[^}]*outline-color:\s*var\(--brand\)/);
});

test("the locked dependency tree includes the native bindings used by Ubuntu CI", async () => {
  const lock = JSON.parse(await readFile(new URL("../package-lock.json", import.meta.url), "utf8"));
  for (const name of ["@tailwindcss/oxide-linux-x64-gnu", "lightningcss-linux-x64-gnu", "@rolldown/binding-linux-x64-gnu"]) {
    const entry = lock.packages[`node_modules/${name}`];
    assert.ok(entry?.version, `${name} must not disappear when the lockfile is updated on Windows`);
    assert.ok(entry.os.includes("linux"));
    assert.ok(entry.cpu.includes("x64"));
  }
});
