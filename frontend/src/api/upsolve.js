import http from "./http";
import { authHeaders } from "./auth";

export async function listUpsolvePlans(completed, page = 1, signal) {
  return (await http.get("/api/upsolve-plans", {
    headers: authHeaders(), params: { completed, page, size: 20 }, signal,
  })).data;
}
export async function saveUpsolvePlan(id, payload) {
  const config = { headers: authHeaders() };
  return (await (id == null ? http.post("/api/upsolve-plans", payload, config)
    : http.patch(`/api/upsolve-plans/${id}`, payload, config))).data;
}
export async function completeUpsolvePlan(id, completed) {
  return (await http.patch(`/api/upsolve-plans/${id}/completion`, { completed }, { headers: authHeaders() })).data;
}
export async function deleteUpsolvePlan(id) {
  return (await http.delete(`/api/upsolve-plans/${id}`, { headers: authHeaders() })).data;
}
