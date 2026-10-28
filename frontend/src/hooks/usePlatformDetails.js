import { useEffect, useState } from "react";
import http from "../api/http.js";
import { authHeaders } from "../api/auth.js";

export default function usePlatformDetails(userId, platform = null, enabled = true) {
  const key = `${userId}:${platform || "all"}`;
  const [state, setState] = useState({ key: null, data: null, loading: true, error: "" });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!userId || !enabled) return;
    let active = true, timer, polls = 0;
    const controller = new AbortController();
    setState({ key, data: null, loading: true, error: "" });
    async function load() {
      if (polls && document.visibilityState !== "visible") return;
      try {
        const result = await http.get(`/api/users/${userId}/platform-details${platform ? `/${platform}` : ""}`, { headers: authHeaders(), signal: controller.signal });
        if (!active) return;
        if (result.data?.code !== 200) throw new Error("公开记录暂不可用");
        const data = result.data.data;
        setState({ key, data, loading: false, error: "" });
        const rows = Array.isArray(data) ? data : [data];
        if (rows.some(row => ["PENDING", "SYNCING"].includes(row?.status)) && polls++ < 12 && document.visibilityState === "visible")
          timer = window.setTimeout(load, Math.min(2500 * 2 ** (polls - 1), 10000));
      } catch (error) { if (active) setState(current => ({ ...current, loading: false, error: error.message || "公开记录暂不可用" })); }
    }
    load();
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [userId, platform, enabled, revision, key]);
  async function sync() {
    if (!userId || !enabled) return;
    try {
      await http.post(`/api/users/${userId}/platform-details${platform ? `/${platform}` : ""}/refresh`, {}, { headers: authHeaders() });
      setRevision(value => value + 1);
    } catch { setState(current => ({ ...current, error: "同步请求未成功，请稍后重试" })); }
  }
  return { ...(enabled && state.key === key ? state : { data: null, loading: Boolean(userId), error: "" }), reload: () => setRevision(value => value + 1), sync };
}
