import { useMemo, useState } from "react";
import usePlatformDetails from "../../hooks/usePlatformDetails";
import { PROFILE_PLATFORMS } from "../../utils/platformPanel";
import { buildPlatformActivity } from "../../utils/platformActivity";
import Heatmap from "../ui/Heatmap";
import { CardSkeleton } from "../ui/Skeleton";
import "./PlatformDetails.css";

export default function PlatformActivity({ userId }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState("active");
  const [selectedDay, setSelectedDay] = useState(null);
  const [selected, setSelected] = useState(PROFILE_PLATFORMS.map(item => item.key));
  const { data, loading, error, sync } = usePlatformDetails(userId, null, open);
  const rows = Array.isArray(data) ? data : [];
  const model = useMemo(() => buildPlatformActivity(rows, selected, mode), [data, selected, mode]);
  const names = Object.fromEntries(PROFILE_PLATFORMS.map(item => [item.key, item.label]));
  const dateMap = new Map(model.data.map(day => [day.date, day]));
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const dayLabel = day => {
    const item = dateMap.get(day.date);
    return !item || !item.score ? `${day.date}：暂无已同步记录，不代表当天没有做题`
      : `${day.date}：${Object.entries(item.sources).map(([platform, value]) => `${names[platform]} ${value.count} ${value.metric === "AC_SUBMISSIONS" ? "次 AC" : "条公开活动"}`).join("；")}`;
  };
  return <section className="oj-activity-section">
    <button type="button" className="oj-activity-disclosure" aria-expanded={open} onClick={() => setOpen(value => !value)}><span>跨平台做题足迹</span><span>{open ? "收起" : "展开查看"}</span></button>
    {open && <div className="oj-activity-body">
      <div className="oj-activity-controls"><div role="group" aria-label="热力图统计口径">{[["active", "活跃平台"], ["ac", "AC 次数"]].map(([value, label]) => <button type="button" key={value} aria-pressed={mode === value} onClick={() => setMode(value)}>{label}</button>)}</div>
        <button type="button" className="button-ghost" onClick={sync} disabled={loading}>同步公开记录</button></div>
      <div className="oj-activity-filters" role="group" aria-label="热力图平台筛选">{PROFILE_PLATFORMS.map(platform => <button type="button" key={platform.key} aria-pressed={selected.includes(platform.key)}
        onClick={() => setSelected(current => current.includes(platform.key) ? current.filter(key => key !== platform.key) : [...current, platform.key])}>
        <img src={platform.icon} width="20" height="20" alt="" />{platform.label}</button>)}</div>
      <p className="oj-source-note">{mode === "active" ? "颜色表示当天有公开做题或活动记录的平台数（1–4 个），同一平台当天只计一次。" : "只汇总已同步的真实 AC 提交次数，重复 AC 计次；洛谷公开活动不混入 AC 次数。"}按北京时间统计最近一年，与站内打卡积分无关。</p>
      {error && <p role="alert">{error}</p>}
      {loading ? <CardSkeleton /> : model.available > 0 ? <>
        <Heatmap data={model.data} showStats={false} activityLabel={mode === "active" ? "Platforms active" : "Accepted submissions"} dayLabel={dayLabel} onDaySelect={setSelectedDay} alignRecent todayDate={today} />
        <p className="oj-source-note" aria-live="polite">{selectedDay ? dayLabel({ date: selectedDay }) : "点击日期查看来源；也可用方向键切换日期。"}</p>
        <dl className="oj-activity-stats"><div><dt>已观察活跃天数</dt><dd>{model.activeDays}</dd></div><div><dt>已同步 AC 次数</dt><dd>{model.acCount.toLocaleString()}</dd></div><div><dt>可用记录来源</dt><dd>{model.available}</dd></div></dl>
      </> : <p role="status">{rows.some(row => ["PENDING", "SYNCING"].includes(row.status)) ? "首次同步中，请稍候。" : selected.length ? mode === "ac" ? "所选平台暂无可用 AC 记录；洛谷公开活动请切换到活跃平台口径查看。" : "所选平台暂无可用公开记录。" : "请选择至少一个平台。"}</p>}
      <ul className="oj-source-list">{rows.filter(row => row.accountId).map(row => <li key={row.platform}><strong>{names[row.platform]}</strong><span>{["PENDING", "SYNCING"].includes(row.status) ? "后台同步中" : row.status === "UNAVAILABLE" ? "暂不可用" : row.status === "STALE" ? "更新失败，保留缓存" : !Array.isArray(row.activity) ? "无日期记录" : row.partial ? "部分公开记录" : "已同步"}</span>{row.note && <p>{row.note}</p>}</li>)}</ul>
      <p className="oj-source-note">受平台公开范围、提交条数及分页限制，空白日期不保证当天没有做题；换绑后不会混入原账号记录。</p>
    </div>}
  </section>;
}
