import http from "./http.js";
import { authHeaders } from "./auth.js";

export async function getPlatformOverviews(userId, signal) {
  const { data } = await http.get(`/api/users/${userId}/platform-overviews`, { headers: authHeaders(), signal });
  if (data?.code !== 200 || !Array.isArray(data.data)) throw new Error(data?.message || "统计暂时无法加载");
  return data.data;
}

export async function getCodeforcesOverview(id) {
  const res = await http.get(`/api/users/${id}/codeforces-overview`, {
    headers: authHeaders(),
  });
  return res.data;
}

export async function refreshCodeforcesOverview(id) {
  const res = await http.post(
    `/api/users/${id}/codeforces-overview/refresh`,
    {},
    { headers: authHeaders(), timeout: 45000 }
  );
  return res.data;
}
