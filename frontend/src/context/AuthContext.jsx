import { createContext, useCallback, useContext, useEffect, useReducer } from "react";
import { login as apiLogin, logout as apiLogout } from "../api/auth";
import { registerUser } from "../api/user";
import { buildAuthFromLogin, clearStoredAuth, getStoredAuth, setStoredAuth } from "../auth";

const AuthContext = createContext(null);

function reducer(state, action) {
  switch (action.type) {
    case "RESTORE": {
      const stored = getStoredAuth();
      return {
        user: stored?.user ?? null,
        tokens: stored?.tokens ?? null,
        isAuthenticated: Boolean(stored?.tokens?.accessToken),
        isAdmin: stored?.user?.role === "ADMIN" || stored?.user?.role === "SUPER_ADMIN",
        isSuperAdmin: stored?.user?.role === "SUPER_ADMIN",
        isLoading: false,
      };
    }
    case "LOGIN": {
      const auth = buildAuthFromLogin(action.payload);
      if (!auth) return state;
      setStoredAuth(auth);
      return {
        user: auth.user,
        tokens: auth.tokens,
        isAuthenticated: true,
        isAdmin: auth.user?.role === "ADMIN" || auth.user?.role === "SUPER_ADMIN",
        isSuperAdmin: auth.user?.role === "SUPER_ADMIN",
        isLoading: false,
      };
    }
    case "UPDATE_USER": {
      const stored = getStoredAuth();
      const nextUser = { ...state.user, ...action.payload };
      const tokens = stored?.user?.id === state.user?.id ? stored.tokens : state.tokens;
      const nextAuth = { user: nextUser, tokens };
      setStoredAuth(nextAuth);
      return {
        ...state,
        user: nextUser,
        tokens,
        isAdmin: nextUser?.role === "ADMIN" || nextUser?.role === "SUPER_ADMIN",
        isSuperAdmin: nextUser?.role === "SUPER_ADMIN",
      };
    }
    case "UPDATE_TOKENS": {
      const stored = getStoredAuth();
      const currentUser = stored?.user || state.user;
      const nextAuth = { user: currentUser, tokens: action.payload };
      setStoredAuth(nextAuth);
      return { ...state, user: currentUser, tokens: action.payload };
    }
    case "LOGOUT": {
      clearStoredAuth();
      return {
        user: null,
        tokens: null,
        isAuthenticated: false,
        isAdmin: false,
        isSuperAdmin: false,
        isDevBackend: false,
        isLoading: false,
      };
    }
    case "SET_DEV_MODE":
      return { ...state, isDevBackend: action.payload };
    default:
      return state;
  }
}

export function AuthProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, {
    user: null,
    tokens: null,
    isAuthenticated: false,
    isAdmin: false,
    isSuperAdmin: false,
    isDevBackend: false,
    isLoading: true,
  });

  useEffect(() => {
    dispatch({ type: "RESTORE" });
  }, []);

  // Listen for auth invalidation from http interceptor (token refresh failed)
  useEffect(() => {
    const handler = () => dispatch({ type: "LOGOUT" });
    const tokensHandler = (event) => dispatch({ type: "UPDATE_TOKENS", payload: event.detail });
    const storageHandler = (event) => {
      if (event.key === "whut-training-auth" || event.key === null) dispatch({ type: "RESTORE" });
    };
    window.addEventListener("auth:invalid", handler);
    window.addEventListener("auth:tokens", tokensHandler);
    window.addEventListener("storage", storageHandler);
    return () => {
      window.removeEventListener("auth:invalid", handler);
      window.removeEventListener("auth:tokens", tokensHandler);
      window.removeEventListener("storage", storageHandler);
    };
  }, []);

  const login = useCallback(async (payload) => {
    const resp = await apiLogin(payload);
    if (resp.code !== 200) {
      return { ok: false, message: resp.message || "登录失败" };
    }
    dispatch({ type: "LOGIN", payload: resp.data });
    return { ok: true };
  }, []);

  const register = useCallback(async (payload) => {
    const resp = await registerUser(payload);
    if (resp.code !== 200) {
      return { ok: false, message: resp.message || "注册失败" };
    }
    return { ok: true };
  }, []);

  const logout = useCallback(async () => {
    try {
      if (state.tokens?.accessToken && state.tokens?.refreshToken) {
        await apiLogout();
      }
    } catch {
      // best effort
    }
    dispatch({ type: "LOGOUT" });
  }, [state.tokens]);

  const updateUser = useCallback((updatedUser) => {
    dispatch({ type: "UPDATE_USER", payload: updatedUser });
  }, []);

  const updateTokens = useCallback((tokens) => {
    dispatch({ type: "UPDATE_TOKENS", payload: tokens });
  }, []);

  const setDevMode = useCallback((active) => {
    dispatch({ type: "SET_DEV_MODE", payload: active });
  }, []);

  return (
    <AuthContext.Provider
      value={{
        ...state,
        login,
        register,
        logout,
        updateUser,
        updateTokens,
        setDevMode,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
