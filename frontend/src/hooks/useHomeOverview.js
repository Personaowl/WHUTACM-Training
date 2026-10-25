import { useCallback, useEffect, useState } from "react";
import { getHomeOverview } from "../api/home.js";
import { getHeatmap } from "../api/user.js";

// Owns request lifecycle and polling; the page owns presentation.
export default function useHomeOverview(userId) {
  const [data, setData] = useState(null);
  const [heatmapData, setHeatmapData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [heatmapLoading, setHeatmapLoading] = useState(true);
  const [error, setError] = useState("");
  const [heatmapError, setHeatmapError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setLoading(true);
    setHeatmapLoading(Boolean(userId));
    setError("");
    setHeatmapError("");
    setHeatmapData([]);

    getHomeOverview(10)
      .then((overview) => {
        if (!cancelled) setData(overview);
      })
      .catch(() => {
        if (!cancelled) setError("加载首页数据失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    if (userId) {
      getHeatmap(userId, 365)
        .then((heatmap) => {
          if (!cancelled && heatmap?.code === 200) {
            setHeatmapData(heatmap.data || []);
          } else if (!cancelled) {
            setHeatmapError("热力图暂时无法加载");
          }
        })
        .catch(() => {
          if (!cancelled) setHeatmapError("热力图暂时无法加载");
        })
        .finally(() => {
          if (!cancelled) setHeatmapLoading(false);
        });

    } else {
      setHeatmapData([]);
      setHeatmapLoading(false);
    }

    return () => { cancelled = true; };
  }, [userId, reloadKey]);

  useEffect(() => {
    if (!data?.problemPoolInitializing) return undefined;

    let requesting = false;
    let cancelled = false;
    const timer = window.setInterval(async () => {
      if (requesting || document.visibilityState !== "visible") return;
      requesting = true;
      try {
        const overview = await getHomeOverview(10);
        if (!cancelled) {
          setData(overview);
          setError("");
        }
      } catch {
        // Keep the current overview visible and retry while the pool is initializing.
      } finally {
        requesting = false;
      }
    }, 3000);

    return () => { cancelled = true; window.clearInterval(timer); };
  }, [data?.problemPoolInitializing, userId]);

  const reload = useCallback(() => setReloadKey(key => key + 1), []);
  return { data, heatmapData, loading, heatmapLoading, error, heatmapError, reload };
}
