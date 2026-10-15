import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Activity,
  CalendarDays,
  CheckCircle2,
  Code2,
  Flame,
  RefreshCw,
  Search,
  Users,
} from "lucide-react";
import { getTrainingDashboard } from "../../api/admin";
import UserAvatar from "../../components/ui/UserAvatar";
import EmptyState from "../../components/ui/EmptyState";
import { getRatingMeta } from "../../utils/cf";
import TrainingExportButton from "../../components/admin/TrainingExportButton";

const FILTERS = [
  { value: "all", label: "全部成员" },
  { value: "completed", label: "今日已完成" },
  { value: "pending", label: "今日待完成" },
  { value: "inactive", label: "近 7 天未活跃" },
];

export default function AdminTrainingDashboardPage() {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");

  async function load({ silent = false } = {}) {
    if (silent) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const response = await getTrainingDashboard();
      if (response.code === 200) {
        setDashboard(response.data);
      } else {
        setError(response.message || "训练数据加载失败");
      }
    } catch {
      setError("训练数据加载失败，请确认后端服务正常运行");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const members = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return (dashboard?.members || []).filter((member) => {
      const matchesKeyword = !keyword || [
        member.username,
        member.displayName,
        member.codeforcesHandle,
      ].some((value) => String(value || "").toLowerCase().includes(keyword));

      if (!matchesKeyword) return false;
      if (filter === "completed") return member.todayCompleted;
      if (filter === "pending") return !member.todayCompleted;
      if (filter === "inactive") return member.sevenDayCompletedDays === 0;
      return true;
    });
  }, [dashboard, filter, query]);

  if (loading) return <DashboardSkeleton />;

  const summary = dashboard?.summary || {};
  const trend = dashboard?.sevenDayTrend || [];
  const maxTrend = Math.max(summary.activeMemberCount || 0, 1);

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="m-0 text-xl font-bold tracking-tight text-text-primary">每日一题完成情况</h2>
          <p className="m-0 mt-2 text-sm text-text-secondary">查看现役队员每日题完成情况、活跃趋势与训练记录。</p>
        </div>
        <div className="flex items-center gap-2">
          <TrainingExportButton defaultType="daily" />
          <button
            type="button"
            className="button-primary inline-flex items-center gap-2"
            disabled={refreshing}
            onClick={() => load({ silent: true })}
          >
            <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
            {refreshing ? "刷新中" : "刷新数据"}
          </button>
        </div>
      </header>

      {error && (
        <div className="rounded-xl border border-[#e3c6c6] bg-[#fff5f5] px-4 py-3 text-sm text-error">
          {error}
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <SummaryCard
          label="现役队员"
          value={summary.activeMemberCount ?? 0}
          suffix="人"
          icon={Users}
          hint="已标记为现役队员"
        />
        <SummaryCard
          label="今日完成"
          value={summary.todayCompletedCount ?? 0}
          suffix={`/ ${summary.activeMemberCount ?? 0}`}
          icon={CheckCircle2}
          hint="至少完成一道每日题"
        />
        <SummaryCard
          label="近 7 天活跃"
          value={summary.sevenDayActiveCount ?? 0}
          suffix="人"
          icon={Activity}
          hint={`整体打卡率 ${summary.sevenDayCompletionRate ?? 0}%`}
        />
      </section>

      <section>
        <div className="rounded-2xl border border-border bg-white p-5 shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h3 className="m-0 text-base font-bold text-text-primary">近 7 天每日完成趋势</h3>
              <p className="m-0 mt-1 text-xs text-text-secondary">按当天至少完成一道每日题的人数统计</p>
            </div>
            <CalendarDays size={20} className="text-text-secondary" />
          </div>
          <div className="mt-6 flex h-44 items-end gap-2 sm:gap-4">
            {trend.map((day) => {
              const height = day.completedMembers === 0
                ? 6
                : Math.max(14, Math.round(day.completedMembers * 100 / maxTrend));
              return (
                <div key={day.date} className="flex min-w-0 flex-1 flex-col items-center gap-2">
                  <span className="text-xs font-bold text-text-primary">{day.completedMembers}</span>
                  <div className="flex h-28 w-full items-end rounded-lg bg-bg-secondary p-1">
                    <div
                      className="w-full rounded-md bg-text-primary transition-[height] duration-500"
                      style={{ height: `${height}%` }}
                      title={`${day.date}：${day.completedMembers} 人完成`}
                    />
                  </div>
                  <span className="truncate font-mono text-[9px] font-bold text-text-secondary">
                    {formatShortDate(day.date)}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
        <div className="flex flex-col gap-3 border-b border-border p-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h3 className="m-0 text-base font-bold text-text-primary">成员训练明细</h3>
            <p className="m-0 mt-1 text-xs text-text-secondary">共显示 {members.length} 名现役队员</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <label className="flex min-h-10 items-center gap-2 rounded-xl border border-border bg-bg-secondary px-3 focus-within:border-text-primary">
              <Search size={15} className="text-text-secondary" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索成员或 CF 账号"
                className="w-full border-0 bg-transparent text-sm text-text-primary outline-none sm:w-48"
              />
            </label>
            <select
              value={filter}
              onChange={(event) => setFilter(event.target.value)}
              className="min-h-10 rounded-xl border border-border bg-white px-3 text-sm text-text-primary outline-none focus:border-text-primary"
            >
              {FILTERS.map((item) => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>
          </div>
        </div>

        {members.length === 0 ? (
          <div className="py-10">
            <EmptyState
              title={dashboard?.members?.length ? "没有符合筛选条件的成员" : "暂无现役队员"}
              description={dashboard?.members?.length ? "请调整搜索关键词或状态筛选。" : "请先在用户管理中将成员身份设为“现役队员”。"}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1040px] border-collapse text-sm">
              <thead>
                <tr className="bg-bg-secondary text-left text-[11px] font-bold text-text-secondary">
                  <th className="px-5 py-3">成员</th>
                  <th className="px-4 py-3">今日状态</th>
                  <th className="px-4 py-3">每日题活跃</th>
                  <th className="px-4 py-3">连续打卡</th>
                  <th className="px-4 py-3">自主练习（30 天）</th>
                  <th className="px-4 py-3">Codeforces</th>
                  <th className="px-5 py-3 text-right">积分</th>
                </tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <MemberRow key={member.userId} member={member} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

    </div>
  );
}

function SummaryCard({ label, value, suffix, icon: Icon, hint }) {
  return (
    <div className="rounded-2xl border border-border bg-white p-4 shadow-[0_10px_30px_rgba(17,17,17,0.035)]">
      <div className="flex items-center justify-between text-text-secondary">
        <span className="text-xs font-semibold">{label}</span>
        <Icon size={18} strokeWidth={1.8} />
      </div>
      <div className="mt-5 flex items-baseline gap-1.5">
        <strong className="text-2xl font-bold tracking-tight text-text-primary">{value}</strong>
        {suffix && <span className="text-xs font-semibold text-text-secondary">{suffix}</span>}
      </div>
      <p className="m-0 mt-2 text-[10px] text-text-secondary">{hint}</p>
    </div>
  );
}

function MemberRow({ member }) {
  const ratingMeta = getRatingMeta(member.codeforcesRating);
  const user = {
    username: member.username,
    displayName: member.displayName,
    avatarUrl: member.avatarUrl,
  };

  return (
    <tr className="border-t border-border transition-colors hover:bg-[#fafaf7]">
      <td className="px-5 py-4">
        <Link to={`/members/${member.userId}`} className="flex w-fit items-center gap-3">
          <span className="relative">
            <UserAvatar user={user} size={38} />
            {member.online && <i className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full border-2 border-white bg-[#40c463]" />}
          </span>
          <span className="min-w-0">
            <strong className="block max-w-44 truncate font-bold text-text-primary">
              {member.displayName || member.username}
            </strong>
            <small className="block max-w-44 truncate text-[10px] text-text-secondary">@{member.username}</small>
            {(member.realName || member.college || member.className) && (
              <small
                className="block max-w-52 truncate text-[10px] text-text-secondary"
                title={[member.realName, member.college, member.className].filter(Boolean).join(" · ")}
              >
                {[member.realName, member.college, member.className].filter(Boolean).join(" · ")}
              </small>
            )}
          </span>
        </Link>
      </td>
      <td className="px-4 py-4">
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold ${
          member.todayCompleted
            ? "bg-[#eaf8ed] text-[#216e39]"
            : "bg-[#f3f3ef] text-text-secondary"
        }`}>
          <span className={`h-1.5 w-1.5 rounded-full ${member.todayCompleted ? "bg-[#30a14e]" : "bg-[#aaa]"}`} />
          {member.todayCompleted ? "已完成" : "待完成"}
        </span>
      </td>
      <td className="px-4 py-4">
        <div className="flex items-center gap-3">
          <div className="w-20">
            <div className="h-1.5 overflow-hidden rounded-full bg-bg-secondary">
              <div
                className="h-full rounded-full bg-[#40c463]"
                style={{ width: `${member.sevenDayCompletedDays * 100 / 7}%` }}
              />
            </div>
          </div>
          <span className="whitespace-nowrap text-xs font-bold text-text-primary">{member.sevenDayCompletedDays}/7 天</span>
        </div>
        <small className="mt-1 block text-[10px] text-text-secondary">30 天完成 {member.thirtyDayCompletedDays} 天</small>
      </td>
      <td className="px-4 py-4">
        <span className="inline-flex items-center gap-1.5 font-bold text-text-primary">
          <Flame size={14} className={member.currentStreakDays > 0 ? "text-[#f59e0b]" : "text-text-secondary"} />
          {member.currentStreakDays} 天
        </span>
        <small className="mt-1 block text-[10px] text-text-secondary">
          {member.lastTrainingDate ? `最近 ${formatShortDate(member.lastTrainingDate)}` : "暂无打卡"}
        </small>
      </td>
      <td className="px-4 py-4">
        <span className="inline-flex items-center gap-1.5 font-bold text-text-primary">
          <Code2 size={14} className="text-text-secondary" />
          {member.thirtyDayPracticeDraws} 次
        </span>
        <small className="mt-1 block text-[10px] text-text-secondary">抽题记录</small>
      </td>
      <td className="px-4 py-4">
        {member.codeforcesHandle ? (
          <div>
            <span className="block max-w-36 truncate text-xs font-bold" style={{ color: ratingMeta.color }}>
              {member.codeforcesRating ?? "Unrated"} · {ratingMeta.label}
            </span>
            <small className="mt-1 block max-w-36 truncate text-[10px] text-text-secondary">
              @{member.codeforcesHandle}{member.codeforcesSolvedCount == null ? "" : ` · ${member.codeforcesSolvedCount} 题`}
            </small>
          </div>
        ) : (
          <span className="text-xs text-text-secondary">未绑定</span>
        )}
      </td>
      <td className="px-5 py-4 text-right">
        <strong className="text-base font-bold text-text-primary">{member.totalPoints.toLocaleString()}</strong>
      </td>
    </tr>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-5 animate-pulse">
      <div className="h-16 w-80 rounded-xl bg-[#e8e8e3]" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="h-36 rounded-2xl border border-border bg-white p-4">
            <div className="h-3 w-10 rounded bg-[#ecece8]" />
            <div className="mt-12 h-7 w-24 rounded bg-[#ecece8]" />
          </div>
        ))}
      </div>
      <div className="h-72 rounded-2xl border border-border bg-white" />
    </div>
  );
}

function formatShortDate(value) {
  if (!value) return "-";
  const parts = String(value).split("-");
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value;
}
