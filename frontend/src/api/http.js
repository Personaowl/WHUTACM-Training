import axios from "axios";
import { clearStoredAuth, getStoredAuth, setStoredAuth } from "../auth.js";

const http = axios.create({
  baseURL: "/",
  timeout: 10000,
});

let refreshingPromise = null;

function shouldSkipRefresh(config) {
  const url = config?.url || "";
  return url.includes("/api/auth/login")
    || url.includes("/api/users/register")
    || url.includes("/api/auth/refresh");
}

function resolveApiError(error) {
  if (error?.response) {
    return Promise.resolve(error.response);
  }
  return Promise.reject(error);
}

async function refreshAccessToken() {
  const auth = getStoredAuth();
  const refreshToken = auth?.tokens?.refreshToken;
  if (!refreshToken) {
    throw new Error("missing refresh token");
  }

  const response = await axios.post(
    "/api/auth/refresh",
    {},
    { headers: { "X-Refresh-Token": refreshToken }, timeout: 10000 }
  );

  const payload = response?.data;
  if (payload?.code !== 200 || !payload?.data?.accessToken || !payload?.data?.refreshToken) {
    throw new Error("refresh failed");
  }

  const nextTokens = {
    accessToken: payload.data.accessToken,
    refreshToken: payload.data.refreshToken,
  };
  const currentAuth = getStoredAuth();
  if (currentAuth?.tokens?.refreshToken !== refreshToken) {
    throw new Error("登录状态已改变，请重试");
  }
  const nextAuth = { ...currentAuth, tokens: nextTokens };
  setStoredAuth(nextAuth);
  window.dispatchEvent(new CustomEvent("auth:tokens", { detail: nextTokens }));
  return nextTokens;
}

// Reuse the latest token for requests created before another request finished refreshing.
http.interceptors.request.use((config) => {
  if (shouldSkipRefresh(config) || !config.headers?.Authorization) return config;
  const auth = getStoredAuth();
  if (config._authUserId != null && config._authUserId !== auth?.user?.id) {
    throw new Error("登录账号已改变，请重试");
  }
  config._authUserId = auth?.user?.id;
  if (auth?.tokens?.accessToken) {
    config.headers.Authorization = `Bearer ${auth.tokens.accessToken}`;
    config.headers["X-Refresh-Token"] = auth.tokens.refreshToken;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error?.config;
    const status = error?.response?.status;

    if (!config || status !== 401 || config._retry || shouldSkipRefresh(config)) {
      return resolveApiError(error);
    }

    config._retry = true;
    const refreshTokenAtStart = getStoredAuth()?.tokens?.refreshToken;

    try {
      const currentTokens = getStoredAuth()?.tokens;
      if (currentTokens?.accessToken
          && config.headers?.Authorization !== `Bearer ${currentTokens.accessToken}`) {
        return http(config);
      }
      if (!refreshingPromise) {
        refreshingPromise = refreshAccessToken().finally(() => {
          refreshingPromise = null;
        });
      }
      const tokens = await refreshingPromise;
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${tokens.accessToken}`;
      config.headers["X-Refresh-Token"] = tokens.refreshToken;
      return http(config);
    } catch (refreshError) {
      const refreshStatus = refreshError?.response?.status;
      const currentRefreshToken = getStoredAuth()?.tokens?.refreshToken;
      // Timeouts, 429 and server outages do not invalidate an otherwise valid session.
      if ((refreshStatus === 401 || refreshStatus === 403 || !currentRefreshToken)
          && currentRefreshToken === refreshTokenAtStart) {
        clearStoredAuth();
        window.dispatchEvent(new CustomEvent("auth:invalid"));
      }
      return resolveApiError(refreshError);
    }
  }
);

export default http;
