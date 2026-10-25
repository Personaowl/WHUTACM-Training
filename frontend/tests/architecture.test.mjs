import assert from "node:assert/strict";
import { test } from "node:test";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../src/", import.meta.url));
async function sources(directory = root) {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...await sources(file));
    else if (/\.(js|jsx)$/.test(file)) result.push(file);
  }
  return result;
}
function imports(source) {
  return [...source.matchAll(/(?:^|\n)\s*(?:import|export)\s+(?:[^"']+?\s+from\s+)?["']([^"']+)["']/g),
    ...source.matchAll(/\bimport\(\s*["']([^"']+)["']\s*\)/g)].map(match => match[1]);
}

test("all frontend modules are reachable from the actual entry", async () => {
  const files = await sources(), available = new Set(files), visited = new Set();
  async function visit(file) {
    if (visited.has(file)) return;
    visited.add(file);
    for (const reference of imports(await readFile(file, "utf8"))) {
      if (!reference.startsWith(".")) continue;
      const target = path.resolve(path.dirname(file), reference);
      const resolved = [target, `${target}.js`, `${target}.jsx`].find(candidate => available.has(candidate));
      if (resolved) await visit(resolved);
    }
  }
  await visit(path.join(root, "main.jsx"));
  assert.deepEqual(files.filter(file => !visited.has(file)).map(file => path.relative(root, file)), []);
});

test("UI does not bypass API modules and lower layers do not import UI", async () => {
  for (const file of await sources()) {
    const relative = path.relative(root, file).replaceAll("\\", "/");
    const references = imports(await readFile(file, "utf8"));
    if (/^(pages|components)\//.test(relative)) {
      assert.ok(!references.some(value => value === "axios" || /\/api\/http(?:\.js)?$/.test(value)), relative);
    }
    if (/^(api|utils)\//.test(relative)) {
      assert.ok(!references.some(value => /^react(?:-|$)/.test(value) || /\/(pages|components|context|hooks)\//.test(value)), relative);
    }
  }
});

test("feature APIs and home presentation keep their responsibility boundaries", async () => {
  const daily = await readFile(path.join(root, "api/dailyProblem.js"), "utf8");
  const user = await readFile(path.join(root, "api/user.js"), "utf8");
  const page = await readFile(path.join(root, "pages/HomePage.jsx"), "utf8");
  assert.doesNotMatch(daily, /\/api\/practice/);
  assert.doesNotMatch(user, /codeforces-binding|atcoder-binding|codeforces-overview/);
  assert.match(page, /useHomeOverview\(user\?\.id\)/);
  assert.doesNotMatch(page, /setInterval|useEffect|getHomeOverview\(|getHeatmap\(/);
});
