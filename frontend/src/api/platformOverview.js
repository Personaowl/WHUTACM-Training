import http from "./http";
import { authHeaders } from "./auth";

export async function getPlatformOverviews(userId, signal) {
  const { data } = await http.get(`/api/users/${userId}/platform-overviews`, { headers: authHeaders(), signal });
  if (data?.code !== 200 || !Array.isArray(data.data)) throw new Error(data?.message || "统计暂时无法加载");
  return data.data;
}
