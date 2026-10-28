import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("the shared sky is static, decorative and does not load assets or add event work", async () => {
  const source = await readFile(new URL("../src/components/ui/AmbientSky.jsx", import.meta.url), "utf8");
  assert.match(source, /aria-hidden="true" focusable="false"/);
  assert.match(source, /className="ambient-sky" aria-hidden="true"/);
  assert.match(source, /<SkyEdge side="left" \/><SkyEdge side="right" \/>/);
  assert.match(source, /className="sky-dust" d=\{DUST_PATH\}/);
  assert.match(source, /className="sky-planet"/);
  assert.match(source, /r=\{r \* 1\.5\}/);
  assert.match(source, /<radialGradient id=\{glowId\}/);
  assert.match(source, /className="sky-glint-halo"/);
  assert.match(source, /className="sky-glint-shape" d=\{GLINT_PATHS\[side\]\}/);
  assert.match(source, /ambient-glow-\$\{useId\(\)/);
  assert.doesNotMatch(source, /useEffect|useState|useRef|setInterval|setTimeout|requestAnimationFrame|Math\.random|fetch\(|addEventListener|<canvas|<filter|<image|<animate|https?:\/\//);
  const layout = await readFile(new URL("../src/components/layout/AppLayout.jsx", import.meta.url), "utf8");
  assert.equal((layout.match(/<AmbientSky \/>/g) || []).length, 1);
  assert.ok(layout.indexOf("<AmbientSky />") > layout.indexOf('className="app-frame"'));
  assert.ok(layout.indexOf("<AmbientSky />") < layout.indexOf('<main id="main-content"'));
});

test("the sky stays under content, cannot intercept clicks and simplifies on narrow or high-contrast views", async () => {
  const css = await readFile(new URL("../src/components/ui/AmbientSky.css", import.meta.url), "utf8");
  assert.match(css, /\.ambient-sky\s*\{[^}]*position: absolute;[^}]*z-index: 0;[^}]*pointer-events: none;/);
  assert.doesNotMatch(css, /animation:|filter:|backdrop-filter:|will-change:|url\(/);
  assert.match(css, /@media \(max-width: 980px\)[\s\S]*?\.ambient-sky-edge \{ display: none;/);
  assert.match(css, /@media \(forced-colors: active\), \(prefers-contrast: more\)[\s\S]*?\.ambient-sky \{ display: none;/);
  const shell = await readFile(new URL("../src/styles/app-shell.css", import.meta.url), "utf8");
  assert.match(shell, /\.app-main\s*\{[^}]*position: relative;[^}]*z-index: 1;/);
  assert.match(shell, /width: min\(100%, 1540px\)/);
});
