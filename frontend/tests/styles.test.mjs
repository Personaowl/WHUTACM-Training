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

test("the locked dependency tree includes the native bindings used by Ubuntu CI", async () => {
  const lock = JSON.parse(await readFile(new URL("../package-lock.json", import.meta.url), "utf8"));
  for (const name of ["@tailwindcss/oxide-linux-x64-gnu", "lightningcss-linux-x64-gnu", "@rolldown/binding-linux-x64-gnu"]) {
    const entry = lock.packages[`node_modules/${name}`];
    assert.ok(entry?.version, `${name} must not disappear when the lockfile is updated on Windows`);
    assert.ok(entry.os.includes("linux"));
    assert.ok(entry.cpu.includes("x64"));
  }
});

test("shared reading sizes enlarge routed pages and dialogs without scaling the app", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  assert.match(source, /--ui-body:\s*17px/);
  assert.match(source, /--ui-small:\s*15px/);
  assert.match(source, /\.text-sm\s*\{[^}]*font-size:\s*var\(--ui-body\)/);
  assert.match(source, /\.text-xs\s*\{[^}]*font-size:\s*var\(--ui-small\)/);
  assert.doesNotMatch(source, /(?:^|[;{])\s*zoom\s*:/m);
});

test("page headings stay between 30 and 32px without resizing metric values", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  assert.match(source, /--page-title-size:\s*clamp\(30px,\s*2\.2vw,\s*32px\)/);
  assert.match(source, /\.auth-card-heading h1\s*\{[^}]*font-size:\s*var\(--page-title-size\)/);
  assert.match(refinements, /\.app-content h1\[class\]\s*\{[^}]*font-size:\s*var\(--page-title-size\)/);
  assert.match(source, /\.text-2xl\s*\{[^}]*font-size:\s*var\(--ui-title\)/);
});

test("sidebar scrollbars are hidden while the navigation remains keyboard-scrollable", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  const sidebar = await readFile(new URL("../src/components/ui/Sidebar.jsx", import.meta.url), "utf8");
  assert.match(source, /\.sidebar-nav\s*\{[^}]*overflow:\s*hidden auto;[^}]*scrollbar-width:\s*none/s);
  assert.match(source, /\.sidebar-nav::\-webkit-scrollbar\s*\{[^}]*display:\s*none/);
  assert.match(sidebar, /<nav[^>]*className="sidebar-nav"[^>]*tabIndex=\{0\}/);
});

test("sidebar toggle matches navigation text size and control height", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  const sidebar = await readFile(new URL("../src/components/ui/Sidebar.jsx", import.meta.url), "utf8");
  assert.match(source, /\.sidebar-toggle\s*\{[^}]*height:\s*46px;[^}]*font-size:\s*var\(--ui-body\)/);
  assert.match(sidebar, /<PanelLeftOpen size=\{19\} \/>\s*:\s*<PanelLeftClose size=\{19\} \/>/);
});

test("auth keeps a larger form, legible fields and optional campus information", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  const register = await readFile(new URL("../src/pages/RegisterPage.jsx", import.meta.url), "utf8");
  assert.match(source, /\.auth-card\s*\{[^}]*width:\s*min\(100%,\s*620px\)/s);
  assert.match(source, /\.auth-field\s*\{[^}]*height:\s*58px;[^}]*font-size:\s*18px/s);
  assert.match(source, /\.auth-form label > span\s*\{[^}]*font-size:\s*17px/);
  assert.match(source, /\.button-primary\.auth-submit\s*\{[^}]*min-height:\s*58px/);
  assert.match(source, /\.auth-intro-copy > p\.auth-intro-affiliation\s*\{[^}]*font-size:\s*18px/s);
  assert.ok(register.includes('auth-form auth-register-form'));
  assert.ok(register.includes('校内信息（选填）'));
});
