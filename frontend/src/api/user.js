import http from "./http";
import { authHeaders } from "./auth";

export async function registerUser(payload) {
  const res = await http.post("/api/users/register", payload);
  return res.data;
}

export async function listUsers() {
  const res = await http.get("/api/users", { headers: authHeaders() });
  return res.data;
}

export async function getUserById(id) {
  const res = await http.get(`/api/users/${id}`, { headers: authHeaders() });
  return res.data;
}

export async function updateMyProfile(id, payload) {
  const res = await http.patch(`/api/users/${id}`, payload ?? {}, {
    headers: authHeaders(),
  });
  return res.data;
}

export async function getHeatmap(userId, days = 180) {
  const res = await http.get(`/api/users/${userId}/daily-heatmap`, {
    headers: authHeaders(),
    params: { days },
  });
  return res.data;
}

export async function getPublicProfile(id) {
  const res = await http.get(`/api/users/${id}/public-profile`, {
    headers: authHeaders(),
  });
  return res.data;
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

export async function startCodeforcesBinding(userId, handle) {
  const res = await http.post(
    `/api/users/${userId}/codeforces-binding/start`,
    { handle },
    { headers: authHeaders() }
  );
  return res.data;
}

export async function finishCodeforcesBinding(userId) {
  const res = await http.post(
    `/api/users/${userId}/codeforces-binding/finish`,
    {},
    { headers: authHeaders() }
  );
  return res.data;
}

export async function startAtCoderBinding(userId, handle) {
  const res = await http.post(
    `/api/users/${userId}/atcoder-binding/start`,
    { handle },
    { headers: authHeaders() }
  );
  return res.data;
}

export async function finishAtCoderBinding(userId) {
  const res = await http.post(
    `/api/users/${userId}/atcoder-binding/finish`,
    {},
    { headers: authHeaders() }
  );
  return res.data;
}
