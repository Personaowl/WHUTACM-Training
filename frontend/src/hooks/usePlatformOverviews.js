import { useEffect, useState } from "react";
import { getPlatformOverviews } from "../api/platformOverview";
import { needsPlatformPolling } from "../utils/platformOverview.js";

// Poll only while a background sync is pending, with no overlap and a finite budget.
export default function usePlatformOverviews(userId, enabled = true) {
  const [state, setState] = useState({ userId: null, overviews: {}, loading: true, error: "" });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!userId || !enabled) return undefined;
    let active = true;
    let timer;
    let polls = 0;
    const controller = new AbortController();
    setState({ userId, overviews: {}, loading: true, error: "" });
    async function load() {
      if (polls > 0 && document.visibilityState !== "visible") return;
      try {
        const rows = await getPlatformOverviews(userId, controller.signal);
        if (!active) return;
        setState({ userId, overviews: Object.fromEntries(rows.map(item => [item.platform, item])), loading: false, error: "" });
        if (needsPlatformPolling(rows) && polls++ < 12 && document.visibilityState === "visible") {
          timer = window.setTimeout(load, Math.min(2500 * 2 ** (polls - 1), 10000));
        }
      } catch (error) {
        if (active) setState(current => ({ ...current, loading: false, error: error.message || "统计暂时无法加载" }));
      }
    }
    load();
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [userId, enabled, revision]);
  const current = state.userId === userId && enabled ? state : { overviews: {}, loading: Boolean(userId), error: "" };
  return { ...current, reload: () => setRevision(value => value + 1) };
}
