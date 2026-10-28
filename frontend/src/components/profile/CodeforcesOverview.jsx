import { useEffect, useState } from "react";
import { ExternalLink, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import {
  getCodeforcesOverview,
  refreshCodeforcesOverview,
} from "../../api/platformOverview";
import { getRatingMeta } from "../../utils/cf";
import { CardSkeleton } from "../ui/Skeleton";
import { TagSolvedChart, RatingSolvedChart } from "./CodeforcesCharts";
import RatingHistoryChart from "./RatingHistoryChart";


export default function CodeforcesOverview({
  userId,
  handle,
  canRefresh = false,
  compact = false,
  detailsOnly = false,
  onUpdated,
}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(Boolean(userId && handle));
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!userId || !handle) {
      setData(null);
      setLoading(false);
      return;
    }
    let cancelled = false;
    let timer;
    let polls = 0;
    async function load() {
      if (!polls) setLoading(true);
      setError("");
      try {
        const response = await getCodeforcesOverview(userId);
        if (!cancelled) {
          if (response.code === 200) {
            setData(response.data);
            if (response.data?.stale && response.data?.ratingHistory == null && polls++ < 12 && document.visibilityState === "visible")
              timer = window.setTimeout(load, Math.min(2500 * 2 ** (polls - 1), 10000));
          }
          else setError(response.message || "Codeforces 数据加载失败");
        }
      } catch {
        if (!cancelled) setError("Codeforces 数据暂时不可用");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [userId, handle]);

  async function handleRefresh() {
    setRefreshing(true);
    setError("");
    try {
      const response = await refreshCodeforcesOverview(userId);
      if (response.code === 200) {
        setData(response.data);
        onUpdated?.();
      }
      else setError(response.message || "刷新失败");
    } catch {
      setError("刷新失败，请稍后重试");
    } finally {
      setRefreshing(false);
    }
  }

  if (!handle) {
    return compact ? null : (
      <section className="bg-white border border-border rounded-ui p-5">
        <h2 className="text-base font-semibold text-text-primary m-0">Codeforces 数据</h2>
        <p className="text-sm text-text-secondary mt-2 mb-0">
          绑定 Codeforces 账号后，这里会展示做题数、标签分布和近期 rated 比赛。
        </p>
      </section>
    );
  }

  if (loading) {
    return <CardSkeleton />;
  }

  if (error && !data) {
    return (
      <section className="bg-white border border-border rounded-ui p-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-text-primary m-0">
              Codeforces 数据
            </h2>
            <p className="text-sm text-error mt-1 mb-0">{error}</p>
          </div>
          {canRefresh && (
            <button className="button-primary inline-flex items-center gap-1.5" onClick={handleRefresh}>
              重试
            </button>
          )}
        </div>
      </section>
    );
  }

  const ratingMeta = getRatingMeta(data?.currentRating);

  if (compact) {
    return (
      <section className="cf-compact-overview bg-white border border-border rounded-ui p-4">
        <div className="cf-compact-heading flex items-center justify-between gap-3 mb-3">
          <div>
            <h2 className="m-0 mb-1 text-sm font-bold text-text-primary">Codeforces 概览</h2>
            <a
              href={`https://codeforces.com/profile/${encodeURIComponent(handle)}`}
              target="_blank"
              rel="noreferrer"
              className="cf-compact-handle text-sm font-semibold text-text-primary no-underline hover:underline"
            >
              @{handle}
            </a>
          </div>
          <Link to="/profile" className="text-xs text-text-secondary hover:text-text-primary">
            查看完整数据 →
          </Link>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <CompactStat label="已解决" value={data?.solvedCount ?? 0} />
          <CompactStat
            label="当前 Rating"
            value={data?.currentRating ?? "-"}
            valueColor={ratingMeta.color}
          />
          <CompactStat label="Rated 比赛" value={data?.ratedContestCount ?? 0} />
        </div>
      </section>
    );
  }

  return (
    <section className="stack-y-3">
      <div className="cf-overview-toolbar flex items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-text-primary m-0">
            {detailsOnly ? "做题与比赛记录" : "Codeforces 数据"}
          </h2>
          {!detailsOnly && <div className="flex flex-wrap items-center gap-2 mt-1">
            <a
              href={`https://codeforces.com/profile/${encodeURIComponent(handle)}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-xs text-text-secondary hover:text-text-primary"
            >
              @{handle} <ExternalLink size={12} />
            </a>
            {data?.stale && (
              <span className="text-[11px] text-text-secondary">
                {data?.syncedAt ? "正在后台更新，当前显示上次快照" : "正在后台同步完整数据"}
              </span>
            )}
          </div>}
        </div>
        {canRefresh && (
          <button
            className="button-primary inline-flex items-center gap-1.5"
            onClick={handleRefresh}
            disabled={refreshing}
          >
            <RefreshCw size={14} className={refreshing ? "animate-spin" : ""} />
            {refreshing ? "同步中..." : "同步 CF"}
          </button>
        )}
      </div>

      {error && <p className="text-xs text-error m-0">{error}</p>}

      {!detailsOnly && <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <OverviewStat label="已解决题目" value={data?.solvedCount ?? 0} />
        <OverviewStat label="尝试过题目" value={data?.attemptedCount ?? 0} />
        <OverviewStat
          label="当前 Rating"
          value={data?.currentRating ?? "-"}
          valueColor={ratingMeta.color}
          hint={`最高 ${data?.maxRating ?? "-"}`}
        />
        <OverviewStat label="Rated 比赛" value={data?.ratedContestCount ?? 0} />
      </div>}

      {data?.submissionLimitReached && (
        <p className="text-[11px] text-text-secondary m-0">
          该账号提交超过 10,000 条，做题统计基于最近 10,000 条提交。
        </p>
      )}

      <RatingHistoryChart platform="codeforces" items={data?.ratingHistory} pending={data?.stale} />

      <div className="cf-detail-grid">
        <TagSolvedChart items={data?.tagStats || []} />
        <RecentContests items={data?.recentContests || []} />
        <RatingSolvedChart items={data?.ratingStats} stale={data?.stale} />
      </div>

      {data?.syncedAt && (
        <p className="text-[11px] text-text-secondary text-right m-0">
          数据同步于 {new Date(data.syncedAt).toLocaleString("zh-CN")}
        </p>
      )}
    </section>
  );
}

function OverviewStat({ label, value, hint, valueColor }) {
  return (
    <div className="bg-white border border-border rounded-ui p-4">
      <p className="text-xs font-semibold tracking-wide text-text-secondary m-0">
        {label}
      </p>
      <strong
        className="block text-2xl leading-none mt-3 text-text-primary"
        style={valueColor ? { color: valueColor } : undefined}
      >
        {value}
      </strong>
      {hint && <small className="block text-xs text-text-secondary mt-1">{hint}</small>}
    </div>
  );
}

function CompactStat({ label, value, valueColor }) {
  return (
    <div className="cf-compact-stat bg-bg-secondary rounded-ui px-3 py-2.5">
      <span className="block text-[11px] font-semibold text-text-secondary">{label}</span>
      <strong
        className="block text-lg text-text-primary mt-0.5"
        style={valueColor ? { color: valueColor } : undefined}
      >
        {value}
      </strong>
    </div>
  );
}


export function RecentContests({ items }) {
  return (
    <div className="bg-white border border-border rounded-ui overflow-hidden">
      <h3 className="text-sm font-semibold text-text-primary m-0 px-4 py-3 border-b border-border">
        近期 Rated 比赛
      </h3>
      {items.length === 0 ? (
        <p className="text-sm text-text-secondary my-12 text-center">暂无 rated 比赛记录</p>
      ) : (
        <div>
          {items.map((contest, index) => (
            <a
              key={`${contest.contestId}-${contest.ratingUpdateTimeSeconds}`}
              href={contest.url}
              target="_blank"
              rel="noreferrer"
              className={`grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-3 px-4 py-3 no-underline hover:bg-bg-secondary ${
                index ? "border-t border-border" : ""
              }`}
            >
              <span className="min-w-0">
                <strong className="block text-sm text-text-primary truncate">
                  {contest.contestName || `Contest ${contest.contestId}`}
                </strong>
                <small className="text-[11px] text-text-secondary">
                  {formatContestDate(contest.ratingUpdateTimeSeconds)} · Rank {contest.rank ?? "-"}
                </small>
              </span>
              <span className="text-xs text-text-secondary">
                {contest.oldRating ?? "-"} → {contest.newRating ?? "-"}
              </span>
              <strong
                className={`text-sm min-w-10 text-right ${
                  (contest.ratingChange ?? 0) > 0
                    ? "text-success"
                    : (contest.ratingChange ?? 0) < 0
                      ? "text-error"
                      : "text-text-secondary"
                }`}
              >
                {(contest.ratingChange ?? 0) > 0 ? "+" : ""}
                {contest.ratingChange ?? 0}
              </strong>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

function formatContestDate(seconds) {
  if (!seconds) return "时间未知";
  return new Date(seconds * 1000).toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}
