import { useId, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ChartNoAxesCombined, CheckCircle2, ChevronDown, ExternalLink, Link2, RefreshCw } from "lucide-react";
import usePlatformOverviews from "../../hooks/usePlatformOverviews";
import { formatPlatformMetric, matchingPlatformOverview, platformRatingMeta } from "../../utils/platformOverview";
import { PROFILE_PLATFORMS, availablePlatformMetrics, nextPlatformTab, platformProfileUrl, profilePlatformAccount } from "../../utils/platformPanel";
import CodeforcesOverview from "./CodeforcesOverview";
import "./PlatformDataPanel.css";

export default function PlatformDataPanel({ profile }) {
  const { overviews, loading, error, reload } = usePlatformOverviews(profile?.id);
  const [choice, setChoice] = useState(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const tabs = useRef([]);
  const id = useId();
  const selectedKey = choice?.userId === profile?.id ? choice.platform
    : PROFILE_PLATFORMS.find(item => profilePlatformAccount(item.key, profile, overviews))?.key || "codeforces";
  const selected = PROFILE_PLATFORMS.find(item => item.key === selectedKey);
  const account = profilePlatformAccount(selectedKey, profile, overviews);
  const data = matchingPlatformOverview(overviews[selectedKey], account);
  const metrics = availablePlatformMetrics(selectedKey, data);
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

  return <section className="profile-platform-panel" aria-labelledby={`${id}-heading`}>
    <div className="profile-platform-title"><span><ChartNoAxesCombined size={21} aria-hidden="true" /><h2 id={`${id}-heading`}>平台数据</h2></span>
      <Link to="/account-binding"><Link2 size={16} aria-hidden="true" />管理绑定</Link>
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
      <div className="profile-platform-account">
        <div className="profile-platform-identity"><img src={selected.icon} alt="" width="46" height="46" />
          <div><h3>{selected.label}</h3>
            {account ? <a href={profileUrl} target="_blank" rel="noopener noreferrer">{account}<ExternalLink size={14} aria-hidden="true" /></a>
              : <span className="profile-platform-account-state">{loading ? "读取账号中" : failed ? "账号状态暂不可用" : "尚未绑定"}</span>}
          </div>
        </div>
        {account && <span className="profile-platform-verified"><CheckCircle2 size={15} aria-hidden="true" />已绑定</span>}
      </div>
      {loading ? <div className="profile-platform-loading" role="status">正在读取平台数据…</div>
        : !account ? <div className="profile-platform-empty">
          <p>{failed ? "账号状态读取失败，请重试。" : `绑定 ${selected.label} 后查看训练数据。`}</p>
          {failed ? <button type="button" className="button-ghost" onClick={reload}>重新加载</button>
            : <Link className="button-primary" to="/account-binding">前往绑定</Link>}
        </div>
          : <>
            {metrics.length > 0 ? <dl className="profile-platform-metrics" aria-label={`${selected.label} 核心统计`}>
              {metrics.map(metric => {
                const meta = metric.key === "solvedCount" ? null : platformRatingMeta(selectedKey, metric.value);
                return <div key={metric.key} className={`tone-${metric.tone}`}><dt>{metric.label}</dt>
                  <dd style={meta ? { color: meta.color } : undefined}>{formatPlatformMetric(metric.value)}</dd>
                  {meta?.label && <small style={{ color: meta.color }}>{meta.label}</small>}
                </div>;
              })}
            </dl> : <div className="profile-platform-empty" role="status">
              <p>{failed ? "平台数据暂时无法获取。" : syncing ? "首次同步中，稍后会自动更新。" : "暂时没有可展示的公开统计。"}</p>
            </div>}
            <div className="profile-platform-footer">
              <span>{failed ? "更新失败" : syncing ? "后台更新中" : updatedText ? `更新于 ${updatedText}` : "尚未同步"}
                {metrics.length > 0 && data?.partial && <span className="profile-platform-partial">仅公开数据</span>}</span>
              <button type="button" onClick={reload} disabled={loading}><RefreshCw size={15} aria-hidden="true" />{failed ? "重试" : "重新加载"}</button>
            </div>
            {selectedKey === "atcoder" && data?.solvedCount != null && <p className="profile-platform-source">解题数来自 AtCoder Problems</p>}
            {selectedKey === "codeforces" && <div className="profile-platform-details">
              <button type="button" className="profile-platform-disclosure" aria-expanded={detailsOpen}
                aria-controls={`${id}-details`} onClick={() => setDetailsOpen(open => !open)}>
                <span><ChartNoAxesCombined size={18} aria-hidden="true" />题目分布与近期比赛</span><ChevronDown size={18} aria-hidden="true" />
              </button>
              <div id={`${id}-details`} hidden={!detailsOpen}>{detailsOpen && <CodeforcesOverview key={`${profile.id}-${account}`}
                userId={profile.id} handle={account} canRefresh detailsOnly onUpdated={reload} />}</div>
            </div>}
          </>}
    </div>
  </section>;
}
