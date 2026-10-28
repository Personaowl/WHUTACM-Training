import { lazy, Suspense, useId, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowUpRight, ChartNoAxesCombined, CheckCircle2, ChevronDown, ExternalLink, Link2, RefreshCw } from "lucide-react";
import usePlatformOverviews from "../../hooks/usePlatformOverviews";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta, platformAccountLabel } from "../../utils/platformOverview";
import { PROFILE_PLATFORMS, availablePlatformMetrics, nextPlatformTab, platformProfileUrl, profilePlatformAccount } from "../../utils/platformPanel";
import { CardSkeleton } from "../ui/Skeleton";
import "./PlatformDataPanel.css";
import "./PlatformMetricTokens.css";

const CodeforcesOverview = lazy(() => import("./CodeforcesOverview"));
const AdditionalPlatformDetails = lazy(() => import("./AdditionalPlatformDetails"));

export default function PlatformDataPanel({ profile, compact = false }) {
  const [searchParams] = useSearchParams();
  const { overviews, loading, error, reload } = usePlatformOverviews(profile?.id);
  const [choice, setChoice] = useState(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const tabs = useRef([]);
  const id = useId();
  const requested = !compact && PROFILE_PLATFORMS.some(item => item.key === searchParams.get("platform")) ? searchParams.get("platform") : null;
  const selectedKey = choice?.userId === profile?.id ? choice.platform
    : requested || PROFILE_PLATFORMS.find(item => profilePlatformAccount(item.key, profile, overviews))?.key || "codeforces";
  const selected = PROFILE_PLATFORMS.find(item => item.key === selectedKey);
  const account = profilePlatformAccount(selectedKey, profile, overviews);
  const data = matchingPlatformOverview(overviews[selectedKey], account);
  const metrics = availablePlatformMetrics(selectedKey, data);
  const accountLabel = platformAccountLabel(selectedKey, account, data);
  const showUid = account && ["luogu", "nowcoder"].includes(selectedKey) && accountLabel !== `UID ${account}`;
  const accountColor = platformRatingMeta(selectedKey, data?.rating).color;
  const syncing = ["PENDING", "SYNCING"].includes(data?.dataStatus);
  const failed = Boolean(error || data?.dataStatus === "UNAVAILABLE");
  const profileUrl = platformProfileUrl(selectedKey, account);
  const updated = data?.syncedAt ? new Date(data.syncedAt) : null;
  const updatedText = updated && !Number.isNaN(updated.getTime()) ? updated.toLocaleString("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }) : null;

  function select(platform) { setChoice({ userId: profile?.id, platform }); }
  function moveTab(event, index) {
    const next = nextPlatformTab(index, event.key);
    if (next === null) return;
    event.preventDefault();
    select(PROFILE_PLATFORMS[next].key);
    tabs.current[next]?.focus();
  }

  return <section className={`profile-platform-panel${compact ? " is-compact" : ""}`} aria-labelledby={`${id}-heading`}>
    <div className="profile-platform-title"><span><ChartNoAxesCombined size={21} aria-hidden="true" /><h2 id={`${id}-heading`}>{compact ? "平台概览" : "平台数据"}</h2></span>
      {compact ? <Link to={`/profile?platform=${selectedKey}`}>详细数据<ArrowUpRight size={16} aria-hidden="true" /></Link>
        : <Link to="/account-binding"><Link2 size={16} aria-hidden="true" />管理绑定</Link>}
    </div>
    <div role="tablist" aria-label="选择数据平台" className="profile-platform-tabs">
      {PROFILE_PLATFORMS.map((platform, index) => <button key={platform.key} type="button" role="tab"
        id={`${id}-tab-${platform.key}`} aria-controls={`${id}-panel`} aria-selected={selectedKey === platform.key}
        tabIndex={selectedKey === platform.key ? 0 : -1} ref={element => { tabs.current[index] = element; }}
        onClick={() => select(platform.key)} onKeyDown={event => moveTab(event, index)}>
        <img src={platform.icon} alt="" width="26" height="26" /><span>{platform.label}</span>
      </button>)}
    </div>
    <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-tab-${selectedKey}`} tabIndex={0}
      className={`profile-platform-body tone-${selected.tone}`} aria-busy={loading}>
      <div className="profile-platform-summary" data-unbound={!loading && !account || undefined}>
      <div className="profile-platform-account">
        <div className="profile-platform-identity">
          <div><h3>{account ? <a href={profileUrl} style={{ color: accountColor }} target="_blank" rel="noopener noreferrer"><span className="profile-platform-name">{accountLabel}</span><ExternalLink size={16} aria-hidden="true" /></a> : selected.label}</h3>
            {showUid && <span className="profile-platform-uid">UID {account}</span>}
            {account ? <span className="profile-platform-verified"><CheckCircle2 size={15} aria-hidden="true" />{selected.label} 已绑定</span>
              : <span className="profile-platform-account-state">{loading ? "读取账号中" : failed ? "账号状态暂不可用" : "尚未绑定"}</span>}
          </div>
        </div>
      </div>
      {loading ? <div className="profile-platform-loading" role="status">正在读取平台数据…</div>
        : !account ? <div className="profile-platform-empty">
          <p>{failed ? "账号状态读取失败，请重试。" : `绑定 ${selected.label} 后查看训练数据。`}</p>
          {failed ? <button type="button" className="button-ghost" onClick={reload}>重新加载</button>
            : <Link className="button-primary" to="/account-binding">前往绑定</Link>}
        </div>
          : metrics.length > 0 ? <dl className="profile-platform-metrics" aria-label={`${selected.label} 核心统计`}>
              {metrics.map((metric, index) => {
                const meta = metric.key === "solvedCount" ? null : platformRatingMeta(selectedKey, metric.value);
                return <div key={metric.key} className={`metric-${metric.key}${index === 0 ? " is-primary" : ""}`}><dt>{metric.label}</dt>
                  <dd style={meta ? { color: meta.color } : undefined}>{formatPlatformMetric(metric.value)}</dd>
                  {meta?.label && <small style={{ color: meta.color }}>{meta.label}</small>}
                </div>;
              })}
            </dl> : <div className="profile-platform-empty" role="status">
              <p>{failed ? "平台数据暂时无法获取。" : syncing ? "首次同步中，稍后会自动更新。" : "暂时没有可展示的公开统计。"}</p>
            </div>}
      </div>
      {!loading && account && <>
            <div className="profile-platform-footer">
              <span>{failed ? "更新失败" : syncing ? "后台更新中" : updatedText ? `更新于 ${updatedText}` : "尚未同步"}
                {metrics.length > 0 && data?.partial && <span className="profile-platform-partial">仅公开数据</span>}</span>
              <button type="button" onClick={reload} disabled={loading}><RefreshCw size={15} aria-hidden="true" />{failed ? "重试" : "重新加载"}</button>
            </div>
            {selectedKey === "atcoder" && data?.solvedCount != null && <p className="profile-platform-source">解题数来自 AtCoder Problems</p>}
            {!compact && account && <div className="profile-platform-details">
              <button type="button" className="profile-platform-disclosure" aria-expanded={detailsOpen}
                aria-controls={`${id}-details`} onClick={() => setDetailsOpen(open => !open)}>
                <span><ChartNoAxesCombined size={18} aria-hidden="true" />{selectedKey === "luogu" ? "已通过题目分布" : "题目分布与近期比赛"}</span><ChevronDown size={18} aria-hidden="true" />
              </button>
              <div id={`${id}-details`} hidden={!detailsOpen}>{detailsOpen && <Suspense fallback={<CardSkeleton />}>{selectedKey === "codeforces" ? <CodeforcesOverview key={`${profile.id}-${account}`}
                userId={profile.id} handle={account} canRefresh detailsOnly onUpdated={reload} /> : <AdditionalPlatformDetails key={`${profile.id}-${selectedKey}-${account}`} userId={profile.id} platform={selectedKey} />}</Suspense>}</div>
            </div>}
          </>}
    </div>
  </section>;
}
