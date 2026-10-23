import { getRatingMeta } from "./cf.js";

export function platformRatingMeta(platform, rating) {
  if (typeof rating !== "number" || !Number.isFinite(rating)) return { color: "#5f6c79", label: "" };
  return platform === "codeforces" ? getRatingMeta(rating) : { color: "#23435e", label: "" };
}

export function formatPlatformMetric(value) {
  return typeof value === "number" && Number.isFinite(value) ? value.toLocaleString("en-US") : "—";
}

export function matchingPlatformOverview(overview, accountId) {
  return accountId && overview?.connected && String(overview.accountId) === String(accountId) ? overview : null;
}
