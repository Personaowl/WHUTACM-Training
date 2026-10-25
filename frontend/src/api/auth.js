import http from "./http.js";
import { getStoredAuth } from "../auth.js";

export function authHeaders() {
  const auth = getStoredAuth();
  const tokens = auth?.tokens;
  if (!tokens?.accessToken || !tokens?.refreshToken) {
    return {};
  }
  return {
    Authorization: `Bearer ${tokens.accessToken}`,
    "X-Refresh-Token": tokens.refreshToken,
  };
}

export async function login(payload) {
  const res = await http.post("/api/auth/login", payload);
  return res.data;
}

export async function logout() {
  const res = await http.post(
    "/api/auth/logout",
    {},
    { headers: authHeaders() }
  );
  return res.data;
}
