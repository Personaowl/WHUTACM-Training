import http from "./http.js";
import { authHeaders } from "./auth.js";
import { checkInPollDelay, waitForCheckInPoll } from "../utils/checkInPolling.js";

export async function getTodayProblem() {
  const res = await http.get("/api/daily-problem/today", { headers: authHeaders() });
  return res.data;
}

export async function checkInToday(submissionId, { signal } = {}) {
  const res = await http.post(
    "/api/daily-problem/check-in",
    { submissionId },
    { headers: authHeaders(), signal }
  );
  if (res.data?.code !== 200 || !res.data?.data?.jobId) return res.data;

  let job = res.data.data;
  for (let attempt = 0; attempt <= 60; attempt += 1) {
    if (job.status === "SUCCEEDED") {
      return { code: 200, message: job.message || "success", data: job.result };
    }
    if (job.status === "FAILED") {
      return { code: job.errorCode || 400, message: job.message || "校验失败", data: null };
    }
    if (attempt === 60) break;
    // The first request is normally a fresh Codeforces submission. Poll quickly
    // at first so a fast check-in does not feel delayed, then back off to avoid
    // unnecessary requests while Codeforces is rate-limited or busy.
    const delayMs = checkInPollDelay(attempt, typeof document !== "undefined" && document.visibilityState === "hidden");
    await waitForCheckInPoll(delayMs, signal);
    const statusResponse = await http.get(
      `/api/daily-problem/check-in/${encodeURIComponent(job.jobId)}`,
      { headers: authHeaders(), signal }
    );
    if (statusResponse.data?.code !== 200) return statusResponse.data;
    job = statusResponse.data.data;
  }
  return { code: 503, message: "校验排队时间较长，请稍后再次查看今日状态", data: null };
}

export async function getDailyHistory(days = 0) {
  const res = await http.get("/api/daily-problem/history", {
    headers: authHeaders(),
    params: { days },
  });
  return res.data;
}

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
