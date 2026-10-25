import http from "./http.js";
import { authHeaders } from "./auth.js";

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
export async function getAdditionalBinding(userId, platform) {
  return (await http.get(`/api/users/${userId}/platform-bindings/${platform}`,
    { headers: authHeaders() })).data;
}

export async function startAdditionalBinding(userId, platform, uid) {
  return (await http.post(`/api/users/${userId}/platform-bindings/${platform}/start`,
    { uid }, { headers: authHeaders() })).data;
}

export async function finishAdditionalBinding(userId, platform) {
  return (await http.post(`/api/users/${userId}/platform-bindings/${platform}/finish`,
    {}, { headers: authHeaders() })).data;
}

