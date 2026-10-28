const PLATFORMS = ["codeforces", "atcoder", "luogu", "nowcoder"];
export function buildPlatformActivity(rows, selected = PLATFORMS, mode = "active", today = null) {
  let first = null;
  if (today) {
    const start = new Date(`${today}T00:00:00Z`);
    start.setUTCDate(start.getUTCDate()-364);
    first = start.toISOString().slice(0,10);
  }
  const usable = rows.filter(row => selected.includes(row.platform) && row.accountId && Array.isArray(row.activity)
    && (mode !== "ac" || row.activityMetric === "AC_SUBMISSIONS"));
  const dates = new Map();
  for (const row of usable) for (const day of row.activity) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day.date) || !Number.isSafeInteger(day.count) || day.count <= 0) continue;
    if (first && (day.date < first || day.date > today)) continue;
    if (!dates.has(day.date)) dates.set(day.date, {});
    dates.get(day.date)[row.platform] = { count: day.count, metric: row.activityMetric };
  }
  const data = [...dates].sort(([a], [b]) => a.localeCompare(b)).map(([date, sources]) => {
    const ac = Object.values(sources).filter(item => item.metric === "AC_SUBMISSIONS").reduce((sum, item) => sum + item.count, 0);
    const records = Object.values(sources).reduce((sum, item) => sum + item.count, 0);
    const score = mode === "training" ? records : mode === "ac" ? ac : Object.keys(sources).length;
    return { date, sources, ac, records, score, colorLevel: score === 0 ? 0 : mode === "active" ? Math.min(4, score) : score < 3 ? 1 : score < 6 ? 2 : score < 10 ? 3 : 4 };
  });
  return { data, available: usable.length, activeDays: data.filter(day => day.score > 0).length, acCount: data.reduce((sum, day) => sum + day.ac, 0), recordCount: data.reduce((sum, day) => sum + day.records, 0) };
}
