import { useMemo, useState } from "react";
import usePlatformDetails from "../../hooks/usePlatformDetails";
import { PROFILE_PLATFORMS } from "../../utils/platformPanel";
import { buildPlatformActivity } from "../../utils/platformActivity";
import Heatmap from "../ui/Heatmap";
import { CardSkeleton } from "../ui/Skeleton";
import "./PlatformDetails.css";

export default function PlatformActivity({ userId }) {
  const [open, setOpen] = useState(false);
  const [selectedDay, setSelectedDay] = useState(null);
  const { data, loading, error, sync } = usePlatformDetails(userId, null, open);
  const rows = Array.isArray(data) ? data : [];
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const model = useMemo(() => buildPlatformActivity(rows, undefined, "training", today), [data, today]);
  const names = Object.fromEntries(PROFILE_PLATFORMS.map(item => [item.key, item.label]));
  const dateMap = new Map(model.data.map(day => [day.date, day]));
  const dayLabel = day => {
    const item = dateMap.get(day.date);
    return !item || !item.score ? `${day.date}：暂无已同步记录，不代表当天没有做题`
      : `${day.date}：${Object.entries(item.sources).map(([platform, value]) => `${names[platform]} ${value.count} ${value.metric === "AC_SUBMISSIONS" ? "次 AC" : "条公开活动"}`).join("；")}`;
  };
  return <section className="oj-activity-section">
    <button type="button" className="oj-activity-disclosure" aria-expanded={open} onClick={() => setOpen(value => !value)}><span>训练热力图</span><span>{open ? "收起" : "展开查看"}</span></button>
    {open && <div className="oj-activity-body">
      <div className="oj-activity-controls"><strong>四平台合并 · 最近一年</strong>
        <button type="button" className="button-ghost" onClick={sync} disabled={loading}>同步公开记录</button></div>
      <div className="oj-activity-platforms" aria-label="已合并的平台">{PROFILE_PLATFORMS.map(platform => <span key={platform.key}>
        <img src={platform.icon} width="22" height="22" alt="" />{platform.label}</span>)}</div>
      <p className="oj-source-note">合并 CF、AtCoder、Nowcoder 的 AC 提交和洛谷公开活动，以训练记录数量呈现热度。它不等于解题数或打卡积分；AC 合计不含洛谷活动。</p>
      {error && <p role="alert">{error}</p>}
      {loading ? <CardSkeleton /> : model.available > 0 ? <>
        <Heatmap data={model.data} showStats={false} activityLabel="Training activity" dayLabel={dayLabel} onDaySelect={setSelectedDay} alignRecent todayDate={today} />
        <p className="oj-source-note" aria-live="polite">{selectedDay ? dayLabel({ date: selectedDay }) : "点击日期查看来源；也可用方向键切换日期。"}</p>
        <dl className="oj-activity-stats"><div><dt>已观察活跃天数</dt><dd>{model.activeDays}</dd></div><div><dt>训练记录</dt><dd>{model.recordCount.toLocaleString()}</dd></div><div><dt>其中 AC 提交</dt><dd>{model.acCount.toLocaleString()}</dd></div><div><dt>可用记录来源</dt><dd>{model.available}<small> / 4</small></dd></div></dl>
      </> : <p role="status">{rows.some(row => ["PENDING", "SYNCING"].includes(row.status)) ? "首次同步中，请稍候。" : "已绑定平台暂无可用公开记录。"}</p>}
      <details className="oj-source-details"><summary>数据来源与统计说明</summary>
        <ul className="oj-source-list">{rows.filter(row => row.accountId).map(row => <li key={row.platform}><strong>{names[row.platform]}</strong><span>{["PENDING", "SYNCING"].includes(row.status) ? "后台同步中" : row.status === "UNAVAILABLE" ? "暂不可用" : row.status === "STALE" ? "更新失败，保留缓存" : !Array.isArray(row.activity) ? "无日期记录" : row.partial ? "部分公开记录" : "已同步"}</span>{row.note && <p>{row.note}</p>}</li>)}</ul>
        <p className="oj-source-note">受平台公开范围、提交条数及分页限制，空白日期不保证当天没有做题；换绑后不会混入原账号记录。</p>
      </details>
    </div>}
  </section>;
}
