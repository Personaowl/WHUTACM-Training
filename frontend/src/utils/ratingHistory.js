const HOSTS = { codeforces: "codeforces.com", atcoder: "atcoder.jp", nowcoder: "ac.nowcoder.com" };

export function ratingContestUrl(platform, url) {
  try {
    const parsed = new URL(url);
    return parsed.origin === `https://${HOSTS[platform]}` && !parsed.username && !parsed.password ? parsed.href : null;
  } catch { return null; }
}

export function normalizeRatingHistory(items, platform) {
  if (!Array.isArray(items)) return null;
  const unique = new Map();
  for (const item of items) {
    if (item.contestId == null || !Number.isSafeInteger(item.newRating) || item.newRating < 0
      || !Number.isSafeInteger(item.ratingUpdateTimeSeconds) || item.ratingUpdateTimeSeconds <= 0) continue;
    const id = String(item.contestId);
    const previous = unique.get(id);
    if (!previous || previous.ratingUpdateTimeSeconds <= item.ratingUpdateTimeSeconds)
      unique.set(id, { ...item, contestId: id, url: ratingContestUrl(platform, item.url) });
  }
  return [...unique.values()].sort((a, b) => a.ratingUpdateTimeSeconds - b.ratingUpdateTimeSeconds || a.contestId.localeCompare(b.contestId)).slice(-500);
}

export function ratingHistoryModel(points) {
  if (!points.length) return null;
  const minimum = Math.min(...points.map(point => point.newRating));
  const maximum = Math.max(...points.map(point => point.newRating));
  const lower = Math.max(0, minimum - 100), upper = maximum + 100;
  const raw = (upper - lower) / 4;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].find(value => value * power >= raw) * power;
  const min = Math.floor(lower / step) * step, max = Math.ceil(upper / step) * step;
  const ticks = Array.from({length: Math.round((max - min) / step) + 1}, (_, index) => min + index * step);
  let start = points[0].ratingUpdateTimeSeconds, end = points.at(-1).ratingUpdateTimeSeconds;
  if (start === end) { start -= 86400; end += 86400; }
  return { min, max, ticks, start, end, peak: maximum, latest: points.at(-1).newRating };
}
