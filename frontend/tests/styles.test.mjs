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

test("the locked dependency tree includes the native bindings used by Ubuntu CI", async () => {
  const lock = JSON.parse(await readFile(new URL("../package-lock.json", import.meta.url), "utf8"));
  for (const name of ["@tailwindcss/oxide-linux-x64-gnu", "lightningcss-linux-x64-gnu", "@rolldown/binding-linux-x64-gnu"]) {
    const entry = lock.packages[`node_modules/${name}`];
    assert.ok(entry?.version, `${name} must not disappear when the lockfile is updated on Windows`);
    assert.ok(entry.os.includes("linux"));
    assert.ok(entry.cpu.includes("x64"));
  }
});
