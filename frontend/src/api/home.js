import http from "./http.js";
import { authHeaders } from "./auth.js";

export async function getHomeOverview(top = 10) {
  const res = await http.get("/api/home", {
    headers: authHeaders(),
    params: { top },
  });
  if (res.status !== 200 || !res.data || typeof res.data !== "object"
      || Array.isArray(res.data) || (res.data.code != null && res.data.code !== 200)) {
    throw new Error("加载首页数据失败");
  }
  // HomeController returns data directly, NOT wrapped in ApiResponse
  return res.data;
}
