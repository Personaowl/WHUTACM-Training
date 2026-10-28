import { getRatingMeta } from "./cf.js";
import { availablePlatformMetrics, platformProfileUrl } from "./platformPanel.js";
import { matchingPlatformOverview, platformAccountLabel } from "./platformOverview.js";
import { ratingBand, ratingBandRange } from "./ratingPalette.js";

const THEMES = {
  codeforces: { accent: "#285eab", tint: "#cfe0ff", source: "Codeforces API" },
  atcoder: { accent: "#965419", tint: "#ffddb6", source: "AtCoder / AtCoder Problems" },
  luogu: { accent: "#15785f", tint: "#bcebdc", source: "洛谷公开资料" },
  nowcoder: { accent: "#7248a6", tint: "#e4d2fa", source: "Nowcoder 公开资料" },
};

// A presentation of the existing verified overview; this model never fetches data.
export function platformSnapshotModel(platform, accountId, overview) {
  if (!accountId || !THEMES[platform]) return null;
  const matched = matchingPlatformOverview(overview, accountId);
  const data = matched && (!matched.platform || matched.platform === platform) ? matched : null;
  const metrics = availablePlatformMetrics(platform, data).filter(item => platform !== "luogu" || item.key === "solvedCount");
  const currentBand = platform !== "luogu" && ratingBand(platform, data?.rating);
  const theme = currentBand ? { accent: currentBand.ink, tint: currentBand.background } : THEMES[platform];
  const name = platformAccountLabel(platform, accountId, data);
  const rank = currentBand ? platform === "codeforces" ? getRatingMeta(data.rating).label : currentBand.label : null;
  return {
    name,
    accountId: String(accountId),
    showUid: ["luogu", "nowcoder"].includes(platform) && name !== `UID ${accountId}`,
    url: platformProfileUrl(platform, accountId),
    accent: theme.accent,
    tint: theme.tint,
    source: THEMES[platform].source,
    rank,
    rankRange: currentBand ? ratingBandRange(currentBand) : null,
    metrics: metrics.map(item => ({ ...item, color: item.key === "solvedCount" ? null : ratingBand(platform, item.value)?.ink,
      rank: item.key !== "solvedCount" && platform === "codeforces" ? getRatingMeta(item.value).label : null })),
  };
}
