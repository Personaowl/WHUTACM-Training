import http from "./http.js";
import { authHeaders } from "./auth.js";

export async function drawPracticeProblem(minRating, maxRating, tags = []) {
  const payload = {};
  if (minRating != null) payload.minRating = Number(minRating);
  if (maxRating != null) payload.maxRating = Number(maxRating);
  if (tags.length > 0) payload.tags = tags;
  const res = await http.post("/api/practice/draw", payload, {
    headers: authHeaders(),
  });
  return res.data;
}

export async function getPracticeTags() {
  const res = await http.get("/api/practice/tags", {
    headers: authHeaders(),
  });
  return res.data;
}

export async function getPracticeHistory(limit = 20) {
  const res = await http.get("/api/practice/history", {
    headers: authHeaders(),
    params: { limit },
  });
  return res.data;
}

export async function deletePracticeDraw(drawId) {
  const res = await http.delete(`/api/practice/${drawId}`, {
    headers: authHeaders(),
  });
  return res.data;
}

