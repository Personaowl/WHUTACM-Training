import usePlatformDetails from "../../hooks/usePlatformDetails";
import { CardSkeleton } from "../ui/Skeleton";
import { RecentContests } from "./CodeforcesOverview";
import "./PlatformDetails.css";

export default function AdditionalPlatformDetails({ userId, platform }) {
  const { data, loading, error, sync } = usePlatformDetails(userId, platform);
  if (loading) return <CardSkeleton />;
  const pending = ["PENDING", "SYNCING"].includes(data?.status);
  return <section className="oj-details">
    <div className="oj-details-toolbar"><h3>{platform === "luogu" ? "已通过题目分布" : "近期 Rating 比赛"}</h3><button type="button" className="button-ghost" onClick={sync}>同步公开记录</button></div>
    {error && <p role="alert">{error}</p>}
    {data?.difficultyStats && <div className="oj-difficulty-card"><h4>洛谷官方难度</h4>
      {data.difficultyStats.length === 0 ? <p>暂无公开的已通过题目</p> : <ul>{data.difficultyStats.map(item => {
        const max = Math.max(1, ...data.difficultyStats.map(row => row.count));
        return <li key={item.label}><span>{item.label}</span><div><i style={{ width: `${item.count / max * 100}%`, background: item.color }} /></div><strong>{item.count.toLocaleString()}</strong></li>;
      })}</ul>}
    </div>}
    {data?.recentContests && <RecentContests items={data.recentContests} />}
    {pending && <p role="status">公开记录正在后台同步，页面会自动更新。</p>}
    {!pending && !data?.difficultyStats && !data?.recentContests && <p>{data?.status === "UNBOUND" ? "请先绑定平台账号。" : "暂时无法取得公开记录，请稍后重试。"}</p>}
    {data?.note && <p className="oj-source-note">{data.note}</p>}
    {data?.syncedAt && <small>更新于 {new Date(data.syncedAt).toLocaleString("zh-CN")}{data.partial ? " · 部分公开数据" : ""}{data.status === "STALE" ? " · 当前为缓存，更新暂不可用" : ""}</small>}
  </section>;
}
