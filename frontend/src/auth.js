const AUTH_STORAGE_KEY = "whut-training-auth";
let cachedRaw = null;
let cachedAuth = null;

function copyAuth(auth) {
  if (!auth || typeof auth !== "object") return auth;
  return {
    ...auth,
    ...(auth.user ? { user: { ...auth.user } } : {}),
    ...(auth.tokens ? { tokens: { ...auth.tokens } } : {}),
  };
}

function formatDisplayDateTime(value) {
  let date = null;
  if (typeof value === "number" && Number.isFinite(value)) {
    date = new Date(value * 1000);
  } else if (typeof value === "string" && value.trim()) {
    date = new Date(value);
  }
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    return "";
  }

  const pad = (value) => String(value).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hour = pad(date.getHours());
  const minute = pad(date.getMinutes());
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

function normalizeAuth(auth) {
  if (!auth?.user) {
    return auth;
  }
  return {
    ...auth,
    user: {
      ...auth.user,
      lastOnlineTimeIso: formatDisplayDateTime(
        auth.user.lastOnlineTimeSeconds ?? auth.user.lastOnlineTimeIso
      )
    }
  };
}

export function getStoredAuth() {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    cachedRaw = null;
    cachedAuth = null;
    return null;
  }

  if (raw === cachedRaw) return copyAuth(cachedAuth);

  try {
    cachedAuth = normalizeAuth(JSON.parse(raw));
    cachedRaw = raw;
    return copyAuth(cachedAuth);
  } catch {
    cachedRaw = null;
    cachedAuth = null;
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function setStoredAuth(auth) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
  cachedRaw = null;
  cachedAuth = null;
}

export function clearStoredAuth() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  cachedRaw = null;
  cachedAuth = null;
}

export function buildAuthFromLogin(data) {
  if (!data?.accessToken || !data?.refreshToken) {
    return null;
  }

  const lastOnline = data.lastOnlineTimeSeconds ?? null;
  const lastOnlineIso = formatDisplayDateTime(lastOnline ?? data.lastOnlineTimeIso);

  return {
    tokens: {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken
    },
    user: {
      id: data.id ?? data.userId ?? null,
      username: data.username ?? "",
      displayName: data.displayName ?? "",
      email: data.email ?? "",
      role: data.role ?? "",
      uid: data.uid ?? null,
      codeforcesRating: data.codeforcesRating ?? null,
      maxRating: data.maxRating ?? null,
      online: data.online ?? null,
      lastOnlineTimeSeconds: lastOnline,
      lastOnlineTimeIso: lastOnlineIso,
      avatarUrl: data.avatarUrl ?? "",
      codeforcesHandle: data.codeforcesHandle ?? null,
      pendingCodeforcesHandle: data.pendingCodeforcesHandle ?? null,
      codeforcesBindingStartedAtSeconds:
        data.codeforcesBindingStartedAtSeconds ?? null
    }
  };
}

export function getUserInitial(user) {
  const source = user?.displayName?.trim() || user?.username?.trim() || user?.email?.trim() || "?";
  return source.charAt(0).toUpperCase();
}
