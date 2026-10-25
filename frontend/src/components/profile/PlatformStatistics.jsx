import { ExternalLink, RefreshCw } from "lucide-react";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta } from "../../utils/platformOverview";
import "./PlatformStatistics.css";

export default function PlatformStatistics({ platform, accountId, overview, loading, error, onRetry }) {
  const data = matchingPlatformOverview(overview, accountId);
  const rating = platformRatingMeta(platform, data?.rating);
  const maximum = platformRatingMeta(platform, data?.maxRating);
  const state = !accountId ? "未绑定" : loading ? "加载中" : error || data?.dataStatus === "UNAVAILABLE"
    ? "统计暂不可用" : ["PENDING", "SYNCING"].includes(data?.dataStatus) ? "同步中" : data?.dataStatus === "READY" ? "" : "未同步";
  const synced = data?.syncedAt ? new Date(data.syncedAt) : null;
  const date = synced && !Number.isNaN(synced.getTime()) ? synced.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : null;
  return <section className="platform-statistics" aria-label="平台统计" aria-busy={loading}>
    <div className="platform-statistics-heading">
      {accountId ? <a href={data?.profileUrl || undefined} target="_blank" rel="noopener noreferrer" style={{ color: rating.color }}>
        <span>{accountId}</span>{data?.profileUrl && <ExternalLink size={13} />}
      </a> : <span className="platform-empty-account">账号统计</span>}
      <span className="platform-data-state">{state || "本地快照"}</span>
    </div>
    <dl className="platform-metrics" aria-label={platform === "atcoder" ? "AtCoder 算法竞赛统计" : "账号基础统计"}>
      <div><dt>当前 Rating</dt><dd style={{ color: rating.color }}>{formatPlatformMetric(data?.rating)}</dd>{rating.label && <small style={{ color: rating.color }}>{rating.label}</small>}</div>
      <div><dt>历史最高</dt><dd style={{ color: maximum.color }}>{formatPlatformMetric(data?.maxRating)}</dd>{maximum.label && <small style={{ color: maximum.color }}>{maximum.label}</small>}</div>
      <div><dt>解题数</dt><dd>{formatPlatformMetric(data?.solvedCount)}</dd><small>题</small></div>
    </dl>
    <div className="platform-statistics-foot">
      <span title={platform === "codeforces" ? "做题统计最多覆盖最近 10000 条提交，数据在后台更新。" : platform === "atcoder" ? "Rating 为算法竞赛分数；解题数来自 AtCoder Problems。" : "仅展示平台公开字段，未公开或暂不可用的指标显示 —。"}>
        {date ? `同步于 ${date}${data.partial ? " · 部分数据" : ""}` : !accountId ? "绑定后查看" : data?.dataStatus === "PENDING" ? "正在获取公开数据" : ""}
      </span>
      {accountId && onRetry && <button type="button" onClick={onRetry} disabled={loading}><RefreshCw size={14} aria-hidden="true" />{error || data?.dataStatus === "UNAVAILABLE" ? "重试" : "重新加载"}</button>}
    </div>
  </section>;
}
