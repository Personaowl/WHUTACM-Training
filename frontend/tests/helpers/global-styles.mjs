import { readFile } from "node:fs/promises";

const entry = new URL("../../src/index.css", import.meta.url);

// Mirror local CSS imports in source-contract tests; Vite separately tests compiled CSS.
async function expand(url, ancestors = new Set()) {
  if (ancestors.has(url.href)) throw new Error(`Circular stylesheet import: ${url.href}`);
  const next = new Set([...ancestors, url.href]);
  let source = await readFile(url, "utf8");
  const imports = [...source.matchAll(/^@import "(\.[^"]+\.css)";\r?$/gm)];
  for (const match of imports.reverse()) {
    const imported = await expand(new URL(match[1], url), next);
    source = source.slice(0, match.index) + imported + source.slice(match.index + match[0].length);
  }
  return source;
}

export function readGlobalStyles() {
  return expand(entry);
}
