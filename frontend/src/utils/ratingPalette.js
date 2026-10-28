// Rating boundaries, not contest divisions. Upper bounds are exclusive.
// CF: https://codeforces.com/blog/entry/20638 + /blog/entry/59228 (2018 update).
// AtCoder: https://info.atcoder.jp/overview/contest/rating and official rating-graph.js.
// Nowcoder: official profile index/main.entry.js colorMap and /acm/contest/134527/A.
// Backgrounds are chart-specific tints; marker colors retain each platform's hue.
const band = (min, max, label, color, background, ink = color) => ({ min, max, label, color, background, ink });

export const RATING_PALETTES = {
  codeforces: [
    band(0, 1200, "灰色", "#747d8c", "#cbd0d7", "#596273"),
    band(1200, 1400, "绿色", "#168238", "#78e67d"),
    band(1400, 1600, "青色", "#07958b", "#6bd8c1", "#08776f"),
    band(1600, 1900, "蓝色", "#4d55df", "#9c9df2"),
    band(1900, 2100, "紫色", "#bb2cbd", "#ed86ee"),
    // Master and International Master share orange; red titles also share a hue.
    band(2100, 2400, "橙色", "#e98713", "#ffd08b", "#995300"),
    band(2400, Infinity, "红色", "#e63d49", "#ff9b9b", "#c52937"),
  ],
  atcoder: [
    band(0, 400, "灰色", "#808080", "#cdd1d6", "#626262"),
    band(400, 800, "茶色", "#804000", "#d4b392"),
    band(800, 1200, "绿色", "#008000", "#a1d0a5"),
    band(1200, 1600, "水色", "#00c0c0", "#98dfdf", "#007c7c"),
    band(1600, 2000, "蓝色", "#0000ff", "#a7aaf4"),
    band(2000, 2400, "黄色", "#c0c000", "#efe18c", "#736900"),
    band(2400, 2800, "橙色", "#ff8000", "#ffc185", "#a64f00"),
    band(2800, Infinity, "红色", "#ff0000", "#ff9c9c", "#cc0000"),
  ],
  nowcoder: [
    band(0, 700, "灰色", "#b4b4b4", "#eceef1", "#626975"),
    band(700, 1100, "紫色", "#c177e7", "#f1e4fa", "#8546ac"),
    band(1100, 1500, "蓝色", "#5ea1f4", "#e0edff", "#2e68b7"),
    band(1500, 2000, "青色", "#25bb9b", "#d9f4ed", "#087e68"),
    band(2000, 2400, "黄色", "#e4c200", "#fff5cc", "#796500"),
    band(2400, 2800, "橙色", "#ff8800", "#ffecd7", "#a65300"),
    band(2800, Infinity, "红色", "#ff020a", "#ffe3e5", "#cc0712"),
  ],
};

export function ratingBands(platform) {
  return RATING_PALETTES[platform] || RATING_PALETTES.codeforces;
}

export function ratingBand(platform, rating) {
  if (!Number.isFinite(rating) || rating < 0) return null;
  return ratingBands(platform).find(item => rating >= item.min && rating < item.max) || null;
}

export function ratingBandRange(item) {
  return Number.isFinite(item.max) ? `${item.min}–${item.max - 1}` : `${item.min}+`;
}
