export const PROFILE_PLATFORMS = [
  { key: "codeforces", label: "Codeforces", icon: "/platforms/codeforces.png", tone: "blue" },
  { key: "atcoder", label: "AtCoder", icon: "/platforms/atcoder.png", tone: "amber" },
  { key: "luogu", label: "洛谷", icon: "/platforms/luogu.ico", tone: "mint" },
  { key: "nowcoder", label: "Nowcoder", icon: "/platforms/nowcoder.ico", tone: "violet" },
];

export function profilePlatformAccount(platform, profile, overviews) {
  const row = overviews?.[platform];
  if (row) return row.connected && row.accountId ? String(row.accountId) : null;
  const field = platform === "codeforces" ? "codeforcesHandle" : platform === "atcoder" ? "atcoderHandle" : null;
  if (field && Object.hasOwn(profile || {}, field)) return profile[field] || null;
  return null;
}

export function platformProfileUrl(platform, account) {
  const origins = { codeforces: "https://codeforces.com/profile/", atcoder: "https://atcoder.jp/users/",
    luogu: "https://www.luogu.com.cn/user/", nowcoder: "https://www.nowcoder.com/users/" };
  return account && origins[platform] ? origins[platform] + encodeURIComponent(account) : null;
}

// Show known metrics, including true zero; missing fields do not occupy empty cards.
export function availablePlatformMetrics(platform, overview) {
  const fields = [
    { key: "rating", label: platform === "atcoder" ? "算法 Rating" : platform === "luogu" ? "比赛 Rating" : "当前 Rating", tone: "blue" },
    { key: "maxRating", label: "历史最高", tone: "violet" },
    { key: "solvedCount", label: "已解决题目", tone: "mint" },
  ];
  return fields.filter(field => typeof overview?.[field.key] === "number" && Number.isFinite(overview[field.key]) && overview[field.key] >= 0)
    .map(field => ({ ...field, value: overview[field.key] }));
}

export function nextPlatformTab(currentIndex, key, count = PROFILE_PLATFORMS.length) {
  if (key === "ArrowRight") return (currentIndex + 1) % count;
  if (key === "ArrowLeft") return (currentIndex + count - 1) % count;
  if (key === "Home") return 0;
  if (key === "End") return count - 1;
  return null;
}
