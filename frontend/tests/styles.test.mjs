import assert from "node:assert/strict";
import { before, test } from "node:test";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { build } from "vite";

test("page icon tiles remain decorative and use one stroke language", async () => {
  const source = await readFile(new URL("../src/components/ui/PageHeading.jsx", import.meta.url), "utf8");
  assert.match(source, /aria-hidden="true"/);
  assert.match(source, /strokeWidth=\{2\.2\}/);
  assert.match(source, /<h1 className="page-title">\{title\}<\/h1>/);
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  for (const tone of ["mint", "violet", "amber"]) assert.ok(refinements.includes(`[data-tone="${tone}"]`));
  assert.match(refinements, /\.page-heading-identity \.page-title\s*\{[^}]*overflow-wrap:\s*anywhere/);
});

test("internal header contours remain static, decorative and behind content", async () => {
  const source = await readFile(new URL("../src/components/ui/SurfaceContours.jsx", import.meta.url), "utf8");
  assert.match(source, /aria-hidden="true" focusable="false"/);
  assert.doesNotMatch(source, /useEffect|setInterval|setTimeout|requestAnimationFrame|fetch\(|<image|<animate/);
  const styles = await readFile(new URL("../src/components/ui/SurfaceContours.css", import.meta.url), "utf8");
  assert.match(styles, /\.surface-contour-host\s*\{[^}]*isolation: isolate/);
  assert.match(styles, /\.surface-contour-host > :not\(\.surface-contours\):not\(\.surface-sheen\)\s*\{[^}]*z-index: 1/);
  assert.match(styles, /\.surface-contours\s*\{[^}]*z-index: 0;[^}]*pointer-events: none/);
  assert.doesNotMatch(styles, /animation:|filter:|backdrop-filter:|will-change:/);
  assert.match(styles, /@media \(forced-colors: active\)\s*\{\s*\.surface-contours\s*\{\s*display: none/);
  const binding = await readFile(new URL("../src/pages/AccountBindingPage.css", import.meta.url), "utf8");
  assert.match(binding, /\.binding-page \.binding-card::before\s*\{[^}]*z-index: 2; pointer-events: none/);
});

test("upsolve actions keep comfortable spacing and consistent control targets", async () => {
  const styles = await readFile(new URL("../src/pages/UpsolvePlanPage.css", import.meta.url), "utf8");
  assert.match(styles, /\.upsolve-actions\s*\{[^}]*gap: 8px/);
  assert.match(styles, /\.upsolve-icon-button\s*\{[^}]*min-width: 44px; min-height: 44px/);
  assert.match(styles, /\.upsolve-pagination button\s*\{[^}]*width: 44px; height: 44px/);
  assert.match(styles, /\.upsolve-checkbox\s*\{[^}]*width: 24px; height: 24px/);
  assert.match(styles, /\.upsolve-actions\s*\{[^}]*grid-column: 1 \/ -1/);
});

test("home training shortcuts follow the library in the lower left column", async () => {
  const source = await readFile(new URL("../src/pages/HomePage.jsx", import.meta.url), "utf8");
  const overview = source.match(/<aside className="home-training-aside"[\s\S]*?<\/aside>/)?.[0];
  assert.ok(overview);
  assert.doesNotMatch(overview, /home-shortcuts/);
  const lower = source.slice(source.indexOf('className="render-lazy home-community-grid"'));
  assert.match(lower, /home-community-aside[\s\S]*home-shared-section[\s\S]*home-shortcuts[\s\S]*home-leaderboard-section/);
  assert.equal((source.match(/aria-label="训练快捷入口"/g) || []).length, 1);
  const styles = await readFile(new URL("../src/pages/HomePage.css", import.meta.url), "utf8");
  assert.match(styles, /\.home-community-aside\s*\{[^}]*min-width: 0;[^}]*gap: 16px/);
});

test("home data cards align naturally without fixed heights or stretched content", async () => {
  const styles = await readFile(new URL("../src/pages/HomePage.css", import.meta.url), "utf8");
  const grid = styles.match(/\.home-training-grid\s*\{([^}]+)\}/)?.[1];
  assert.match(grid, /align-items: stretch/);
  assert.doesNotMatch(grid, /height:|transform:/);
  assert.match(styles, /\.home-training-aside > \.profile-platform-panel\s*\{[^}]*display: flex; flex-direction: column/);
  assert.match(styles, /\.home-training-grid:has\([^}]*profile-platform-empty[^}]*\)\s*\{\s*align-items: start/);
  assert.match(styles, /\.home-training-grid, \.home-community-grid\s*\{[^}]*grid-template-columns: minmax\(0, 1fr\); align-items: start/);
});

test("richer problem surfaces preserve server difficulty and Codeforces rating colors", async () => {
  const card = await readFile(new URL("../src/components/ui/ProblemCard.jsx", import.meta.url), "utf8");
  assert.match(card, /getRatingMeta\(problem.rating\)/);
  assert.match(card, /style=\{\{ color: meta.color \}\}/);
  assert.match(card, /data-slot=\{problem.slot\?\.toLowerCase\(\) \|\| problem.type\?\.toLowerCase\(\)\}/);
  const home = await readFile(new URL("../src/pages/HomePage.css", import.meta.url), "utf8");
  assert.ok(home.includes('[data-slot="easy"]'));
  assert.ok(home.includes('[data-slot="hard"]'));
  assert.match(home, /\.home-stat dd\s*\{[^}]*font-size:\s*26px;[^}]*white-space:\s*nowrap/);
  assert.match(home, /\.home-stat:first-child\s*\{[^}]*grid-column:\s*1 \/ -1/);
});

test("library selection has a semantic state without changing editing permissions", async () => {
  const source = await readFile(new URL("../src/pages/ProblemListsPage.jsx", import.meta.url), "utf8");
  assert.match(source, /aria-pressed=\{selectedId === item.id\}/);
  assert.match(source, /canAddProblem\(detail.list\)/);
  assert.match(source, /canEditProblem\(detail.list, problem, user\?\.id, isAdmin\)/);
});

let css;

test("library row actions stay together and keep clear accessible names", async () => {
  const source = await readFile(new URL("../src/pages/ProblemListsPage.jsx", import.meta.url), "utf8");
  assert.match(source, /className="library-problem-actions" role="group"/);
  assert.match(source, /aria-label="编辑题目" title="编辑题目"/);
  assert.match(source, /aria-label="移除题目" title="移除题目"/);
  assert.match(source, /onOpenAutoFocus=\{\(\) => \{ returnFocus.current = document.activeElement;/);
  assert.match(source, /onCloseAutoFocus=/);
  assert.match(source, /target\?\.focus\(\{ preventScroll: true \}\)/);
  assert.match(source, /<PageHeading icon=\{sharedOnly \? BookCopy : BookOpenText\}/);
  const styles = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  assert.match(styles, /\.library-icon-button[^}]*width:\s*44px;[^}]*min-height:\s*44px/);
});

test("light pass repeats slowly, remains non-interactive and stops under reduced motion", async () => {
  const source = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  const effect = source.match(/\.surface-sheen\s*\{([^}]+)\}/)?.[1];
  assert.ok(effect);
  assert.match(effect, /pointer-events:\s*none/);
  assert.match(effect, /animation:\s*surface-light-pass 14s linear 600ms infinite both/);
  assert.doesNotMatch(effect, /filter|backdrop-filter|will-change/);
  assert.match(source, /@media \(prefers-reduced-motion: reduce\)\s*\{\s*\.surface-sheen\s*\{\s*display:\s*none;\s*animation:\s*none/);
  const component = await readFile(new URL("../src/components/ui/SurfaceSheen.jsx", import.meta.url), "utf8");
  assert.match(component, /aria-hidden="true"/);
  assert.doesNotMatch(component, /requestAnimationFrame|setInterval|setTimeout|fetch\(|<button/);
  assert.match(component, /animationPlayState: visible \? "running" : "paused"/);
  assert.match(component, /observer\?\.disconnect\(\)/);
  assert.match(component, /removeEventListener\("visibilitychange", update\)/);
});

test("auth retains its ink-blue cover with only restrained shared accents", async () => {
  const source = await readFile(new URL("../src/components/ui/AuthFrame.jsx", import.meta.url), "utf8");
  assert.match(source, /auth-shell-bridge/);
  assert.match(source, /className="auth-cover-decoration" aria-hidden="true"/);
  assert.doesNotMatch(source, /AlgorithmCover|auth-capabilities|auth-seam|auth-geometry|canvas|<video/);
  const styles = await readFile(new URL("../src/components/ui/AuthFrame.css", import.meta.url), "utf8");
  assert.match(styles, /linear-gradient\(135deg, #0b1729, #122942 62%, #1a3a55\)/);
  assert.match(styles, /\.auth-shell-bridge \.auth-intro-copy::after/);
  const global = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  assert.doesNotMatch(global, /\.auth-seam-decoration|\.auth-geometry/);
});

test("auth has a crisp curved edge and keeps static artwork behind the form", async () => {
  const source = await readFile(new URL("../src/components/ui/AuthFrame.jsx", import.meta.url), "utf8");
  assert.match(source, /className="auth-background-lines"[^>]*aria-hidden="true" focusable="false"/);
  const styles = await readFile(new URL("../src/components/ui/AuthFrame.css", import.meta.url), "utf8");
  assert.match(styles, /\.auth-shell-bridge \.auth-intro\s*\{[^}]*border-radius: 0 72px 72px 0 \/ 0 50% 50% 0; box-shadow: 4px 0 0/);
  assert.match(styles, /@media \(max-width: 980px\)\s*\{[^}]*border-radius: 0 0 50% 50% \/ 0 0 24px 24px/);
  assert.doesNotMatch(styles, /mask-image:/);
  assert.match(styles, /\.auth-background-lines\s*\{[^}]*z-index: 0;[^}]*height: auto;[^}]*pointer-events: none/);
  assert.match(styles, /\.auth-shell-bridge \.auth-card\s*\{[^}]*z-index: 1/);
  assert.doesNotMatch(styles, /animation:|filter:|backdrop-filter:|will-change:/);
});

test("bottom rings connect the cover without covering form controls or adding motion", async () => {
  const source = await readFile(new URL("../src/components/ui/AuthFrame.jsx", import.meta.url), "utf8");
  assert.match(source, /className="auth-bottom-orbits"[^>]*aria-hidden="true" focusable="false"/);
  const styles = await readFile(new URL("../src/components/ui/AuthFrame.css", import.meta.url), "utf8");
  assert.match(styles, /\.auth-bottom-orbits\s*\{[^}]*z-index: 2;[^}]*bottom: 0;[^}]*pointer-events: none/);
  assert.match(styles, /\.auth-shell-bridge \.auth-card-wrap\s*\{[^}]*z-index: 3/);
  assert.match(styles, /\.auth-bottom-orbits\s*\{\s*display: none/);
});

test("auth keeps a readable static brand accent with color fallback", async () => {
  const source = await readFile(new URL("../src/components/ui/AuthFrame.jsx", import.meta.url), "utf8");
  assert.equal((source.match(/className="auth-brand-title"/g) || []).length, 2);
  const styles = await readFile(new URL("../src/components/ui/AuthFrame.css", import.meta.url), "utf8");
  assert.match(styles, /\.auth-brand-title\s*\{[^}]*color: #b9d9ff/);
  assert.match(styles, /@supports \(\(background-clip: text\)/);
  assert.match(styles, /@media \(forced-colors: active\)\s*\{\s*\.auth-brand-title\s*\{[^}]*background: none; color: CanvasText/);
  const luminance = (hex) => {
    const rgb = hex.match(/[a-f\d]{2}/gi).map((part) => parseInt(part, 16) / 255)
      .map((value) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
    return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
  };
  for (const color of ["a8cdff", "b6c5ff", "a6dfd0", "b9d9ff"]) {
    assert.ok((luminance(color) + 0.05) / (luminance("243e62") + 0.05) >= 4.5);
  }
});

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

test("polished surfaces reuse tokens and preserve stationary interaction feedback", async () => {
  const source = await readFile(new URL("../src/index.css", import.meta.url), "utf8");
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  assert.match(source, /--shadow-surface:/);
  assert.match(source, /--radius-control:\s*12px/);
  assert.match(source, /\.app-main article\.bg-white:hover\s*\{[^}]*transform:\s*none/s);
  assert.match(refinements, /\.training-section-link\[aria-current="page"\]/);
  assert.match(refinements, /\[role="dialog"\] form\s*\{[^}]*scroll-padding-block:/);
});

test("mobile navigation exposes state and restores keyboard access on close", async () => {
  const sidebar = await readFile(new URL("../src/components/ui/Sidebar.jsx", import.meta.url), "utf8");
  const layout = await readFile(new URL("../src/components/layout/AppLayout.jsx", import.meta.url), "utf8");
  assert.ok(sidebar.includes('aria-current={active ? "page" : undefined}'));
  assert.ok(sidebar.includes('aria-controls={groupId}'));
  assert.ok(sidebar.includes('event.key === "Escape"'));
  assert.ok(sidebar.includes('event.key !== "Tab"'));
  assert.ok(sidebar.includes('returnTarget?.focus'));
  assert.ok(layout.includes('href="#main-content"'));
  assert.ok(layout.includes('inert={mobileNavOpen ? "" : undefined}'));
});

test("problem names remain readable without truncating the essential title", async () => {
  const card = await readFile(new URL("../src/components/ui/ProblemCard.jsx", import.meta.url), "utf8");
  assert.doesNotMatch(card, /<h3[^>]*\btruncate\b/);
  assert.ok(card.includes('break-words'));
});

test("compact navigation labels remain whole while their containers can wrap", async () => {
  const refinements = await readFile(new URL("../src/Refinements.css", import.meta.url), "utf8");
  const upsolve = await readFile(new URL("../src/pages/UpsolvePlanPage.css", import.meta.url), "utf8");
  assert.match(refinements, /\.training-section-link\s*\{[^}]*white-space:\s*nowrap/);
  assert.match(upsolve, /\.upsolve-tabs button\s*\{[^}]*white-space:\s*nowrap/);
  assert.match(upsolve, /\.upsolve-toolbar\s*\{[^}]*flex-wrap:\s*wrap/);
});
