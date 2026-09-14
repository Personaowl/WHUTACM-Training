import http from "./http";
import { authHeaders } from "./auth";

export async function adminCreateUser(payload) {
  const res = await http.post("/api/admin/users", payload, { headers: authHeaders() });
  return res.data;
}

export async function changeUserRole(userId, role) {
  const res = await http.put(
    `/api/admin/users/${userId}/role`,
    { role },
    { headers: authHeaders() }
  );
  return res.data;
}

export async function changeUserMemberType(userId, memberType) {
  const res = await http.put(
    `/api/admin/users/${userId}/member-type`,
    { memberType },
    { headers: authHeaders() }
  );
  return res.data;
}

export async function resetUserPassword(userId) {
  const res = await http.post(
    `/api/admin/users/${userId}/reset-password`,
    {},
    { headers: authHeaders() }
  );
  return res.data;
}

export async function regenerateDaily() {
  const res = await http.post(
    "/api/admin/daily-problem/regenerate",
    {},
    { headers: authHeaders() }
  );
  return res.data;
}

export async function redrawSlot(date, slot, confirm = false) {
  const params = new URLSearchParams({ slot, confirm: String(confirm) });
  if (date) params.set("date", date);
  const res = await http.post(
    `/api/admin/daily-problem/redraw?${params}`,
    {},
    { headers: authHeaders() }
  );
  return res.data;
}

export async function getRoles() {
  const res = await http.get("/api/roles", { headers: authHeaders() });
  return res.data;
}

export async function getTrainingDashboard() {
  const res = await http.get("/api/admin/training-dashboard", {
    headers: authHeaders(),
  });
  return res.data;
}

export async function exportTrainingDashboard(payload) {
  const res = await http.post("/api/admin/training-dashboard/export", payload, {
    headers: authHeaders(),
    responseType: "blob",
    timeout: 120000,
  });
  const disposition = res.headers?.["content-disposition"] || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  let filename = "WHUT-ACM_现役队员训练数据.xlsx";
  if (encoded) {
    try {
      filename = decodeURIComponent(encoded);
    } catch {
      filename = encoded;
    }
  }
  return { blob: res.data, filename };
}

export async function getAtCoderAbcDashboard(contestId) {
  const res = await http.get("/api/admin/atcoder-abc", {
    headers: authHeaders(),
    params: contestId ? { contestId } : undefined,
  });
  return res.data;
}

export async function refreshAtCoderAbcDashboard(contestId) {
  const res = await http.post("/api/admin/atcoder-abc/refresh", {}, {
    headers: authHeaders(),
    params: contestId ? { contestId } : undefined,
    timeout: 120000,
  });
  return res.data;
}

export async function updateAtCoderTrackingSetting(payload, contestId) {
  const res = await http.patch("/api/admin/atcoder-abc/setting", payload, {
    headers: authHeaders(),
    params: contestId ? { contestId } : undefined,
  });
  return res.data;
}

export async function updateAtCoderExemption(contestId, userId, payload) {
  const res = await http.patch(
    `/api/admin/atcoder-abc/${contestId}/members/${userId}/exemption`,
    payload,
    { headers: authHeaders() }
  );
  return res.data;
}
