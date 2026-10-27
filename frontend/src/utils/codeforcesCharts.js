export const TAG_COLORS = ["#5273d2", "#88bf59", "#efb744", "#ea6b73", "#58b2c9", "#3fa584", "#f47a52", "#a46ac6", "#d66bb0", "#6a83bc", "#68babb", "#9baac0"];

export function tagDistribution(items = []) {
  const ordered = items.filter(item => typeof item.tag === "string" && Number.isSafeInteger(item.count) && item.count > 0)
    .slice().sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag));
  const top = ordered.slice(0, 11);
  const other = ordered.slice(11).reduce((sum, item) => sum + item.count, 0);
  return other ? [...top, { tag: "其他", count: other }] : top;
}

export function ratingDistribution(items) {
  if (!Array.isArray(items)) return null;
  const counts = new Map(Array.from({ length: 28 }, (_, index) => [800 + index * 100, 0]));
  let unrated = 0;
  for (const item of items) {
    if (!Number.isSafeInteger(item.count) || item.count <= 0) continue;
    if (item.rating === null) unrated += item.count;
    else if (Number.isSafeInteger(item.rating) && item.rating > 0)
      counts.set(item.rating, (counts.get(item.rating) || 0) + item.count);
  }
  const bars = [...counts].sort((a, b) => a[0] - b[0]).map(([rating, count]) => ({ rating, count }));
  const rated = bars.reduce((sum, item) => sum + item.count, 0);
  const peak = bars.reduce((best, item) => item.count > (best?.count || 0) ? item : best, null);
  const max = Math.max(1, ...bars.map(item => item.count));
  const step = 10 ** Math.floor(Math.log10(max / 4));
  const unit = [1, 2, 5, 10].find(value => value * step >= max / 4) * step;
  return { bars, rated, unrated, peak, ceiling: Math.max(1, unit) * 4 };
}

export function difficultyColor(rating) {
  if (rating < 1200) return "#9ba3b2";
  if (rating < 1400) return "#66bc73";
  if (rating < 1600) return "#42b7b0";
  if (rating < 1900) return "#8a80e9";
  if (rating < 2100) return "#d56bd8";
  if (rating < 2400) return "#efae53";
  return "#ed6c76";
}
