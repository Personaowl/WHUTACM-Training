import { ExternalLink, RefreshCw } from "lucide-react";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta } from "../../utils/platformOverview";

export default function PlatformStatistics({ platform, accountId, overview, loading, error, onRetry }) {
  const data = matchingPlatformOverview(overview, accountId);
  const rating = platformRatingMeta(platform, data?.rating);
  const maximum = platformRatingMeta(platform, data?.maxRating);
  const state = !accountId ? "未绑定" : loading ? "加载中" : error || data?.dataStatus === "UNAVAILABLE"
    ? "统计暂不可用" : data?.dataStatus === "READY" ? "" : "未同步";
  const synced = data?.syncedAt ? new Date(data.syncedAt) : null;
  const date = synced && !Number.isNaN(synced.getTime()) ? synced.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : null;
  return <section className="platform-statistics" aria-label="平台统计" aria-busy={loading}>
    <div className="platform-statistics-heading">
      {accountId ? <a href={data?.profileUrl || undefined} target="_blank" rel="noopener noreferrer" style={{ color: rating.color }}>
        <span>{accountId}</span>{data?.profileUrl && <ExternalLink size={13} />}
      </a> : <span className="platform-empty-account">账号统计</span>}
      <span className="platform-data-state">{state || "本地快照"}</span>
    </div>
    <dl className="platform-metrics">
      <div><dt>当前 Rating</dt><dd style={{ color: rating.color }}>{formatPlatformMetric(data?.rating)}</dd>{rating.label && <small style={{ color: rating.color }}>{rating.label}</small>}</div>
      <div><dt>历史最高</dt><dd style={{ color: maximum.color }}>{formatPlatformMetric(data?.maxRating)}</dd>{maximum.label && <small style={{ color: maximum.color }}>{maximum.label}</small>}</div>
      <div><dt>解题数</dt><dd>{formatPlatformMetric(data?.solvedCount)}</dd><small>题</small></div>
    </dl>
    <div className="platform-statistics-foot">
      <span title={platform === "codeforces" ? "来自已有本地快照，做题统计最多覆盖最近 10000 条提交；此页不发起外部同步。" : undefined}>
        {date ? `同步于 ${date}${data.partial ? " · 部分数据" : ""}` : !accountId ? "绑定后查看" : data?.dataStatus === "NOT_SUPPORTED" ? "统计待接入" : data?.dataStatus === "PENDING" ? "尚无快照" : ""}
      </span>
      {accountId && (error || data?.dataStatus === "UNAVAILABLE") && <button type="button" onClick={onRetry} disabled={loading}><RefreshCw size={12} />重试</button>}
    </div>
  </section>;
}
