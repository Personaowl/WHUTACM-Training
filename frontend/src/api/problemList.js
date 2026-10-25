import http from "./http.js";
import { authHeaders } from "./auth.js";

export async function listProblemLists() {
  const res = await http.get("/api/problem-lists", { headers: authHeaders() });
  return res.data;
}

export async function getProblemList(id) {
  const res = await http.get(`/api/problem-lists/${id}`, { headers: authHeaders() });
  return res.data;
}

export async function createProblemList(payload) {
  const res = await http.post("/api/problem-lists", payload, { headers: authHeaders() });
  return res.data;
}

export async function updateProblemList(id, payload) {
  const res = await http.patch(`/api/problem-lists/${id}`, payload, { headers: authHeaders() });
  return res.data;
}

export async function deleteProblemList(id) {
  const res = await http.delete(`/api/problem-lists/${id}`, { headers: authHeaders() });
  return res.data;
}

export async function addProblemListItem(listId, payload) {
  const res = await http.post(`/api/problem-lists/${listId}/items`, payload, { headers: authHeaders() });
  return res.data;
}

export async function updateProblemListItem(listId, itemId, payload) {
  const res = await http.patch(`/api/problem-lists/${listId}/items/${itemId}`, payload, {
    headers: authHeaders(),
  });
  return res.data;
}

export async function deleteProblemListItem(listId, itemId) {
  const res = await http.delete(`/api/problem-lists/${listId}/items/${itemId}`, {
    headers: authHeaders(),
  });
  return res.data;
}
