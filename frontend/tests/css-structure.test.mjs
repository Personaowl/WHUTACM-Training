import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile, readdir } from "node:fs/promises";
import { readGlobalStyles } from "./helpers/global-styles.mjs";

const modules = ["foundation", "controls", "app-shell", "sidebar", "topbar", "surfaces", "profile",
  "heatmap", "authentication", "responsive", "typography", "binding", "responsive-overrides"];

test("global styles retain their explicit cascade order and have no orphan modules", async () => {
  const entry = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  assert.deepEqual([...entry.matchAll(/@import "\.\/styles\/([^".]+)\.css"/g)].map(m => m[1]), modules);
  assert.deepEqual((await readdir(new URL("../src/styles/", import.meta.url))).filter(f => f.endsWith(".css")).sort(),
    modules.map(name => `${name}.css`).sort());
  assert.ok(entry.split("\n").length < 30);
  assert.doesNotMatch(entry, /:root\s*\{|\.app-shell\s*\{/);
});

test("source checks expand global imports while keeping a single Tailwind configuration", async () => {
  const source = await readGlobalStyles();
  assert.equal((source.match(/@config /g) || []).length, 1);
  assert.equal((source.match(/@utility stack-y-/g) || []).length, 1);
  assert.ok(source.indexOf("/* Sidebar */") < source.indexOf("/* Shared reading sizes"));
  assert.ok(source.indexOf("/* Authentication */") < source.indexOf("@media (max-width: 980px)"));
  assert.match(source, /@config "\.\.\/\.\.\/tailwind\.config\.js"/);
  assert.doesNotMatch(source, /@import "\.\/styles\//);
});
