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

export async function logout(tokens) {
  const res = await http.post(
    "/api/auth/logout",
    {},
    { headers: authHeaders() }
  );
  return res.data;
}

export async function refreshTokens() {
  const auth = getStoredAuth();
  const refreshToken = auth?.tokens?.refreshToken;
  if (!refreshToken) throw new Error("missing refresh token");

  const res = await http.post(
    "/api/auth/refresh",
    {},
    { headers: { "X-Refresh-Token": refreshToken } }
  );
  return res.data;
}
