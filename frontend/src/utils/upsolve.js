export function problemHost(link) {
  try {
    const host = new URL(link).hostname.replace(/^www\./, "");
    return ({ "codeforces.com": "Codeforces", "atcoder.jp": "AtCoder", "luogu.com.cn": "洛谷",
      "nowcoder.com": "牛客", "ac.nowcoder.com": "牛客" })[host] || host;
  } catch { return "题目"; }
}

export function upsolveDate(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

export function requireUpsolveData(response) {
  if (response?.code !== 200) throw new Error(response?.message || "操作失败，请稍后重试");
  return response.data;
}
