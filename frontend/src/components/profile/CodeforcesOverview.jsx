import { useEffect, useMemo, useState } from "react";
import { ExternalLink, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import {
  getCodeforcesOverview,
  refreshCodeforcesOverview,
} from "../../api/user";
import { getRatingMeta } from "../../utils/cf";
import { buildTagDistribution } from "../../utils/codeforcesCharts";
import { CardSkeleton } from "../ui/Skeleton";
import "./CodeforcesOverview.css";

export default function CodeforcesOverview({
  userId,
  handle,
  canRefresh = false,
  compact = false,
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
    async function load() {
      setLoading(true);
      setError("");
      try {
        const response = await getCodeforcesOverview(userId);
        if (!cancelled) {
          if (response.code === 200) setData(response.data);
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
    };
  }, [userId, handle]);

  async function handleRefresh() {
    setRefreshing(true);
    setError("");
    try {
      const response = await refreshCodeforcesOverview(userId);
      if (response.code === 200) setData(response.data);
      else setError(response.message || "刷新失败");
    } catch {
      setError("刷新失败，请稍后重试");
    } finally {
      setRefreshing(false);
    }
  }

  if (!handle) {
    return compact ? null : (
      <section className="cf-overview-empty">
        <h2 className="text-base font-semibold text-text-primary m-0">Codeforces 数据</h2>
        <p className="text-sm text-text-secondary mt-2 mb-0">
          尚未绑定 Codeforces。<Link to="/account-binding">前往绑定</Link>
        </p>
      </section>
    );
  }

  if (loading) {
    return <CardSkeleton />;
  }

  if (error && !data) {
    return (
      <section className="cf-overview-empty">
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
    <section className="cf-overview">
      <div className="cf-overview-heading">
        <div>
          <h2>
            Codeforces 数据
          </h2>
          <div className="flex flex-wrap items-center gap-2 mt-1">
            <a
              href={`https://codeforces.com/profile/${encodeURIComponent(handle)}`}
              target="_blank"
              rel="noreferrer"
              className="cf-profile-link"
            >
              @{handle} <ExternalLink size={12} />
            </a>
            {data?.stale && (
              <span className="cf-snapshot-state">
                {data?.syncedAt ? "后台更新中" : "完整数据同步中"}
              </span>
            )}
          </div>
        </div>
        {canRefresh && (
          <button
            className="cf-refresh-button"
            onClick={handleRefresh}
            disabled={refreshing}
          >
            <RefreshCw size={14} className={refreshing ? "animate-spin" : ""} />
            {refreshing ? "同步中..." : "同步 CF"}
          </button>
        )}
      </div>

      {error && <p className="text-xs text-error m-0">{error}</p>}

      <div className="cf-metric-strip">
        <OverviewStat label="已解决题目" value={data?.solvedCount ?? 0} />
        <OverviewStat label="尝试过题目" value={data?.attemptedCount ?? 0} />
        <OverviewStat
          label="当前 Rating"
          value={data?.currentRating ?? "-"}
          valueColor={ratingMeta.color}
          hint={`最高 ${formatNumber(data?.maxRating ?? "-")}`}
        />
        <OverviewStat label="Rated 比赛" value={data?.ratedContestCount ?? 0} />
      </div>

      {data?.submissionLimitReached && (
        <p className="cf-overview-note">
          该账号提交超过 10,000 条，做题统计基于最近 10,000 条提交。
        </p>
      )}

      <div className="cf-detail-grid">
        <TagDistribution items={data?.tagStats || []} />
        <RecentContests items={data?.recentContests || []} />
      </div>

      {data?.syncedAt && (
        <p className="cf-overview-note cf-synced-at">
          数据同步于 {new Date(data.syncedAt).toLocaleString("zh-CN")}
        </p>
      )}
    </section>
  );
}

function OverviewStat({ label, value, hint, valueColor }) {
  return (
    <div className="cf-overview-stat">
      <p>
        {label}
      </p>
      <strong
        className={value === "-" ? "cf-stat-missing" : undefined}
        style={valueColor ? { color: valueColor } : undefined}
      >
        {formatNumber(value)}
      </strong>
      {hint && <small>{hint}</small>}
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
        {formatNumber(value)}
      </strong>
    </div>
  );
}

function TagDistribution({ items }) {
  const distribution = useMemo(() => buildTagDistribution(items), [items]);
  const chartItems = distribution.items;

  return (
    <div className="cf-detail-panel cf-tags-panel">
      <div className="cf-panel-heading">
        <h3>题目标签分布</h3>
        <span>{formatNumber(chartItems.length)} 个标签</span>
      </div>
      {distribution.totalTagCount === 0 ? (
        <p className="cf-empty-row">暂无 AC 题目标签</p>
      ) : (
        <ul className="cf-tag-list" aria-label="已解决题目的标签数量">
          {chartItems.map(item => (
            <li key={item.tag} aria-label={`${item.tag}：${formatNumber(item.count)} 次，占标签计数的 ${item.sharePercent.toFixed(1)}%`} title={`${item.tag}：${formatNumber(item.count)} 次，占标签计数的 ${item.sharePercent.toFixed(1)}%`}>
              <div className="cf-tag-label"><span>{item.tag}</span><strong>{formatNumber(item.count)}</strong></div>
              <span className="cf-tag-bar" style={{ width: `${item.widthPercent}%` }} aria-hidden="true" />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RecentContests({ items }) {
  return (
    <div className="cf-detail-panel cf-contests-panel">
      <div className="cf-panel-heading"><h3>近期 Rated 比赛</h3><span>{items.length} 场</span></div>
      {items.length === 0 ? (
        <p className="cf-empty-row">暂无 rated 比赛记录</p>
      ) : (
        <div>
          {items.map(contest => (
            <a
              key={`${contest.contestId}-${contest.ratingUpdateTimeSeconds}`}
              href={contest.url}
              target="_blank"
              rel="noreferrer"
              className="cf-contest-row"
            >
              <span className="min-w-0">
                <strong className="cf-contest-name">
                  {contest.contestName || `Contest ${contest.contestId}`}
                </strong>
                <small>
                  {formatContestDate(contest.ratingUpdateTimeSeconds)} · Rank {formatNumber(contest.rank ?? "-")}
                </small>
              </span>
              <span className="cf-contest-result">
              <strong
                className={`cf-contest-change ${
                  (contest.ratingChange ?? 0) > 0
                    ? "text-success"
                    : (contest.ratingChange ?? 0) < 0
                      ? "text-error"
                      : "text-text-secondary"
                }`}
              >
                {(contest.ratingChange ?? 0) > 0 ? "+" : ""}
                {formatNumber(contest.ratingChange ?? 0)}
              </strong>
                <small>{formatNumber(contest.oldRating ?? "-")} → {formatNumber(contest.newRating ?? "-")}</small>
              </span>
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

function formatNumber(value) {
  return typeof value === "number" && Number.isFinite(value) ? value.toLocaleString("en-US") : value;
}
