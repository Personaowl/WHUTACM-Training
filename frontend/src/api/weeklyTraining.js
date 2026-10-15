import http from "./http";
import { authHeaders } from "./auth";

export async function getWeeklyTraining(week, admin = false) {
  const response = await http.get(
    admin ? "/api/admin/weekly-training" : "/api/weekly-training/me",
    {
      headers: authHeaders(),
      params: week ? { week } : undefined,
    },
  );
  return response.data;
}

export async function submitWeeklyExplanation(week, payload) {
  const response = await http.post(
    "/api/weekly-training/me/explanation",
    payload,
    {
      headers: authHeaders(),
      params: { week },
    },
  );
  return response.data;
}

export async function reviewWeeklyTraining(week, userId, payload) {
  const response = await http.post(
    `/api/admin/weekly-training/${userId}/review`,
    payload,
    {
      headers: authHeaders(),
      params: { week },
    },
  );
  return response.data;
}
