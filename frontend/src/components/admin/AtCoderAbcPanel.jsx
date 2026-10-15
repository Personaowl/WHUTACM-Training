import { useEffect, useMemo, useState } from "react";
import {
  getAtCoderAbcDashboard,
  refreshAtCoderAbcDashboard,
  updateAtCoderExemption,
  updateAtCoderTrackingSetting,
} from "../../api/admin";
import UserAvatar from "../ui/UserAvatar";
import { CheckCircle2, Clock3, RefreshCw, Settings2, ShieldAlert, Trophy, Users } from "lucide-react";

const STATUS_META = {
  COMPLETED: { label: "已完成", className: "bg-[#e7f7ec] text-[#216e39]" },
  PARTICIPATED: { label: "已参赛·未达标", className: "bg-[#fff5dc] text-[#8a5b00]" },
  ABSENT: { label: "缺席", className: "bg-[#fff0f0] text-error" },
  UNBOUND: { label: "未绑定", className: "bg-[#f1f1ed] text-text-secondary" },
  EXEMPT: { label: "已豁免", className: "bg-[#eaf1f8] text-[#245b8f]" },
  DATA_ERROR: { label: "数据异常", className: "bg-[#fff0f0] text-error" },
  UPCOMING: { label: "待比赛", className: "bg-[#eef4fb] text-[#245b8f]" },
  PENDING: { label: "等待同步", className: "bg-[#f3f3ef] text-text-secondary" },
};

export default function AtCoderAbcPanel() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [minimumAc, setMinimumAc] = useState(1);
  const [graceHours, setGraceHours] = useState(24);

  async function load(contestId, refresh = false) {
    refresh ? setRefreshing(true) : setLoading(true);
    setError("");
    try {
      const response = refresh
        ? await refreshAtCoderAbcDashboard(contestId)
        : await getAtCoderAbcDashboard(contestId);
      if (response.code !== 200) throw new Error(response.message || "ABC 数据加载失败");
      setData(response.data);
      setMinimumAc(response.data?.setting?.minimumAcCount ?? 1);
      setGraceHours(response.data?.setting?.graceHours ?? 24);
    } catch (requestError) {
      setError(requestError?.message || "ABC 数据加载失败");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function saveSetting() {
    setSaving(true);
    setError("");
    try {
      const response = await updateAtCoderTrackingSetting(
        { minimumAcCount: Number(minimumAc), graceHours: Number(graceHours) },
        data?.currentContest?.contestId
      );
      if (response.code !== 200) throw new Error(response.message || "设置保存失败");
      setData(response.data);
    } catch (requestError) {
      setError(requestError?.message || "设置保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function toggleExemption(member) {
    let reason = null;
    if (!member.exempted) {
      reason = window.prompt("请输入本场豁免原因（例如请假、集训冲突）");
      if (!reason?.trim()) return;
    }
    setSaving(true);
    setError("");
    try {
      const response = await updateAtCoderExemption(
        data.currentContest.contestId,
        member.userId,
        { exempted: !member.exempted, reason: reason?.trim() || null }
      );
      if (response.code !== 200) throw new Error(response.message || "豁免设置失败");
      setData(response.data);
    } catch (requestError) {
      setError(requestError?.message || "豁免设置失败");
    } finally {
      setSaving(false);
    }
  }

  const summary = data?.summary || {};
  const current = data?.currentContest;
  const contestState = useMemo(() => {
    if (!current) return "-";
    const now = Date.now() / 1000;
    if (now < current.startTimeSeconds) return "即将开始";
    if (now <= current.endTimeSeconds) return "比赛进行中";
    return current.syncStatus === "FINALIZED" ? "已完成最终同步" : "赛后同步中";
  }, [current]);

  if (loading) return <div className="h-64 animate-pulse rounded-2xl border border-border bg-white" />;

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
      <div className="border-b border-border bg-[#f8fafc] p-5">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div>
            <h3 className="m-0 text-xl font-bold text-text-primary">AtCoder ABC 完成情况</h3>
            <p className="m-0 mt-1 text-xs text-text-secondary">官方历史确认参赛，正式比赛时段内 AC 达到要求后记为完成。</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select value={current?.contestId || ""} onChange={(event) => load(event.target.value)} className="min-h-10 max-w-xs rounded-xl border border-border bg-white px-3 text-sm font-semibold text-text-primary">
              {(data?.contests || []).map((contest) => <option key={contest.contestId} value={contest.contestId}>{contest.contestId.toUpperCase()} · {formatDate(contest.startTimeSeconds)}</option>)}
            </select>
            <button type="button" className="button-primary inline-flex items-center gap-2" onClick={() => load(current?.contestId, true)} disabled={refreshing || !current}>
              <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />{refreshing ? "同步中" : "同步结果"}
            </button>
          </div>
        </div>
        {current && <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-text-secondary"><a href={current.contestUrl} target="_blank" rel="noreferrer" className="font-bold text-text-primary hover:underline">{current.name}</a><span>{formatDateTime(current.startTimeSeconds)} 开始</span><span>{contestState}</span><span>最近同步：{current.lastSyncAt ? new Date(current.lastSyncAt).toLocaleString() : "尚未同步"}</span></div>}
      </div>

      {error && <p className="m-4 rounded-xl border border-[#e3c6c6] bg-[#fff5f5] px-4 py-3 text-sm text-error">{error}</p>}

      <div className="grid gap-3 p-5 sm:grid-cols-2 xl:grid-cols-4">
        <Metric icon={Users} label="应参加" value={summary.requiredCount ?? 0} suffix="人" />
        <Metric icon={CheckCircle2} label="已达标" value={summary.completedCount ?? 0} suffix={` / ${Math.max(0, (summary.requiredCount ?? 0) - (summary.exemptedCount ?? 0))}`} />
        <Metric icon={Trophy} label="正式参赛" value={summary.participatedCount ?? 0} suffix="人" />
        <Metric icon={ShieldAlert} label="完成率" value={summary.completionRate ?? 0} suffix="%" />
      </div>

      <div className="mx-5 mb-5 flex flex-col gap-3 rounded-xl border border-border bg-bg-secondary p-4 lg:flex-row lg:items-end lg:justify-between">
        <div className="flex items-center gap-2 text-sm font-bold text-text-primary"><Settings2 size={16} /> 判定规则</div>
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-xs text-text-secondary">最低 AC 数<select value={minimumAc} onChange={(event) => setMinimumAc(event.target.value)} className="ml-2 min-h-9 rounded-lg border border-border bg-white px-2 text-sm text-text-primary">{Array.from({ length: 9 }).map((_, value) => <option key={value} value={value}>{value}</option>)}</select></label>
          <label className="text-xs text-text-secondary">最终判定等待<select value={graceHours} onChange={(event) => setGraceHours(event.target.value)} className="ml-2 min-h-9 rounded-lg border border-border bg-white px-2 text-sm text-text-primary">{[6, 12, 24, 48, 72].map((value) => <option key={value} value={value}>{value} 小时</option>)}</select></label>
          <button type="button" className="min-h-9 rounded-lg bg-[#122943] px-3 text-xs font-bold text-white" onClick={saveSetting} disabled={saving}>{saving ? "保存中" : "保存规则"}</button>
        </div>
      </div>

      <div className="overflow-x-auto border-t border-border">
        <table className="w-full min-w-[980px] border-collapse text-sm">
          <thead><tr className="bg-bg-secondary text-left text-[11px] font-bold text-text-secondary"><th className="px-5 py-3">成员</th><th className="px-4 py-3">AtCoder</th><th className="px-4 py-3">状态</th><th className="px-4 py-3">比赛完成</th><th className="px-4 py-3">名次 / Performance</th><th className="px-4 py-3">管理</th></tr></thead>
          <tbody>{(data?.members || []).map((member) => {
            const status = STATUS_META[member.status] || STATUS_META.PENDING;
            return <tr key={member.userId} className="border-t border-border hover:bg-[#fafaf7]">
              <td className="px-5 py-4"><div className="flex items-center gap-3"><UserAvatar user={member} size={36} /><span><strong className="block text-text-primary">{member.displayName || member.username}</strong><small className="text-text-secondary">@{member.username}</small></span></div></td>
              <td className="px-4 py-4">{member.atcoderHandle ? <a className="font-bold text-[#245b8f] hover:underline" href={`https://atcoder.jp/users/${member.atcoderHandle}`} target="_blank" rel="noreferrer">@{member.atcoderHandle}</a> : <span className="text-text-secondary">未绑定</span>}</td>
              <td className="px-4 py-4"><span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-bold ${status.className}`}>{status.label}</span>{member.sourceError && <small className="mt-1 block max-w-52 truncate text-error" title={member.sourceError}>{member.sourceError}</small>}</td>
              <td className="px-4 py-4"><strong className="text-text-primary">{member.acCount ?? "-"} AC</strong><small className="mt-1 block max-w-64 truncate text-text-secondary" title={(member.solvedProblemIds || []).join(", ")}>{member.solvedProblemIds?.length ? member.solvedProblemIds.join(", ") : "暂无正式赛时 AC"}</small></td>
              <td className="px-4 py-4"><span className="font-bold text-text-primary">#{member.contestRank ?? "-"}</span><small className="mt-1 block text-text-secondary">Performance {member.performance ?? "-"}</small></td>
              <td className="px-4 py-4"><button type="button" className="rounded-lg border border-border bg-white px-3 py-1.5 text-xs font-bold text-text-primary hover:bg-bg-secondary" disabled={saving} onClick={() => toggleExemption(member)}>{member.exempted ? "取消豁免" : "设为豁免"}</button>{member.exemptionReason && <small className="mt-1 block max-w-44 truncate text-text-secondary" title={member.exemptionReason}>{member.exemptionReason}</small>}</td>
            </tr>;
          })}</tbody>
        </table>
        {!data?.members?.length && <div className="p-8 text-center text-sm text-text-secondary"><Clock3 className="mx-auto mb-2" size={22} />本场尚未生成现役成员检查名单</div>}
      </div>
    </section>
  );
}

function Metric({ icon: Icon, label, value, suffix }) {
  return <div className="rounded-xl border border-border bg-white p-3"><div className="flex items-center justify-between text-text-secondary"><span className="text-xs font-semibold">{label}</span><Icon size={16} /></div><p className="m-0 mt-2 text-xl font-bold text-text-primary">{value}<small className="ml-1 text-xs font-semibold text-text-secondary">{suffix}</small></p></div>;
}
function formatDate(seconds) { return new Date(seconds * 1000).toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" }); }
function formatDateTime(seconds) { return new Date(seconds * 1000).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }); }
