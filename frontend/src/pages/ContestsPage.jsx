import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarClock, Clock3, ExternalLink, Radio, RefreshCw } from "lucide-react";
import { getUpcomingContests } from "../api/contests";
import EmptyState from "../components/ui/EmptyState";
import { ListSkeleton } from "../components/ui/Skeleton";

const SHANGHAI_TIME = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  month: "2-digit",
  day: "2-digit",
  weekday: "short",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const PLATFORM_META = {
  CODEFORCES: { label: "Codeforces", color: "#315fc4", soft: "#edf3ff" },
  ATCODER: { label: "AtCoder", color: "#3f4650", soft: "#f0f1f2" },
  NOWCODER: { label: "牛客", color: "#159963", soft: "#eaf8f1" },
  LUOGU: { label: "洛谷", color: "#dd6818", soft: "#fff2e9" },
};

export default function ContestsPage() {
  const [contests, setContests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const [platformFilter, setPlatformFilter] = useState("ALL");

  const loadContests = useCallback(async (manual = false) => {
    if (manual) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const resp = await getUpcomingContests();
      if (resp.code === 200) {
        setContests(resp.data || []);
      } else {
        setError(resp.message || "近期比赛加载失败");
      }
    } catch (requestError) {
      setError(requestError.response?.data?.message || "近期比赛暂时无法获取");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadContests();
  }, [loadContests]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const sortedContests = useMemo(
    () => [...contests].sort((a, b) => new Date(a.startTime) - new Date(b.startTime)),
    [contests]
  );
  const visibleContests = useMemo(
    () => platformFilter === "ALL"
      ? sortedContests
      : sortedContests.filter((contest) => contest.platform === platformFilter),
    [platformFilter, sortedContests]
  );

  return (
    <div className="stack-y-5">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold tracking-[0.18em] text-text-secondary m-0">
            CONTEST RADAR
          </p>
          <h1 className="text-xl font-bold text-text-primary mt-1 mb-0">近期比赛</h1>
          <p className="text-sm text-text-secondary mt-1 mb-0">
            聚合 Codeforces、AtCoder、牛客与洛谷赛程，统一显示为北京时间。
          </p>
        </div>
        <button
          type="button"
          className="inline-flex items-center justify-center gap-2 px-3 py-2 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary disabled:opacity-50"
          onClick={() => loadContests(true)}
          disabled={refreshing}
        >
          <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
          {refreshing ? "刷新中" : "刷新赛程"}
        </button>
      </div>

      {error && (
        <p className="text-sm text-error bg-[#fff0f0] rounded-ui px-3 py-2 m-0">{error}</p>
      )}

      {!loading && sortedContests.length > 0 && (
        <div className="flex flex-wrap items-center gap-2" aria-label="比赛平台筛选">
          <PlatformFilter
            active={platformFilter === "ALL"}
            label="全部"
            count={sortedContests.length}
            onClick={() => setPlatformFilter("ALL")}
          />
          {Object.entries(PLATFORM_META).map(([platform, meta]) => (
            <PlatformFilter
              key={platform}
              active={platformFilter === platform}
              label={meta.label}
              count={sortedContests.filter((contest) => contest.platform === platform).length}
              color={meta.color}
              onClick={() => setPlatformFilter(platform)}
            />
          ))}
        </div>
      )}

      {loading ? (
        <ListSkeleton rows={6} />
      ) : visibleContests.length === 0 ? (
        <EmptyState title={sortedContests.length === 0 ? "暂时没有公布的近期比赛" : "该平台暂时没有近期比赛"} />
      ) : (
        <div className="grid gap-3">
          {visibleContests.map((contest, index) => {
            const status = getContestStatus(contest, now);
            const platform = PLATFORM_META[contest.platform] || {
              label: contest.platform || "Contest",
              color: "#3f4650",
              soft: "#f0f1f2",
            };
            return (
              <article
                key={`${contest.platform}-${contest.contestId}`}
                className="group bg-white border border-border rounded-ui p-4 sm:p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_10px_28px_rgba(20,24,30,0.08)]"
              >
                <div className="flex items-start gap-4">
                  <div
                    className="hidden sm:flex w-12 h-12 shrink-0 items-center justify-center rounded-full transition-transform duration-200 group-hover:scale-105"
                    style={{ backgroundColor: platform.soft, color: platform.color }}
                  >
                    {status.live ? <Radio size={20} /> : <CalendarClock size={20} />}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className="text-[10px] font-extrabold tracking-[0.12em] px-2 py-1 rounded-full"
                        style={{ color: platform.color, backgroundColor: platform.soft }}
                      >
                        {platform.label.toUpperCase()} · {contest.type?.toUpperCase() || "CONTEST"}
                      </span>
                      <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${
                        status.live
                          ? "bg-[#e8f8eb] text-[#216e39]"
                          : index === 0
                            ? "bg-[#fff4df] text-[#8a5a00]"
                            : "bg-bg-secondary text-text-secondary"
                      }`}>
                        {status.label}
                      </span>
                    </div>
                    <h2 className="text-base sm:text-lg font-bold text-text-primary mt-2 mb-0">
                      {contest.name}
                    </h2>
                    <div className="flex flex-wrap gap-x-5 gap-y-1 mt-3 text-xs text-text-secondary">
                      <span className="inline-flex items-center gap-1.5">
                        <CalendarClock size={14} />
                        {SHANGHAI_TIME.format(new Date(contest.startTime))}
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        <Clock3 size={14} />
                        {formatDuration(contest.durationMinutes)}
                      </span>
                      <span>Rated: {contest.ratedRange || "-"}</span>
                    </div>
                  </div>
                  <a
                    className="inline-flex shrink-0 items-center gap-1 text-xs font-semibold text-text-primary hover:underline"
                    href={contest.url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    前往比赛
                    <ExternalLink size={14} />
                  </a>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <p className="text-xs text-text-secondary m-0">
        数据来自四个平台的官方比赛页或公开接口，后端会分平台短时缓存；具体安排以各平台官方页面为准。
      </p>
    </div>
  );
}

function PlatformFilter({ active, label, count, color, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors ${
        active
          ? "border-text-primary bg-text-primary text-white"
          : "border-border bg-white text-text-secondary hover:text-text-primary hover:border-[#aaa]"
      }`}
    >
      {color && !active && (
        <span className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
      )}
      {label}
      <span className={active ? "text-white/65" : "text-text-secondary"}>{count}</span>
    </button>
  );
}

function getContestStatus(contest, now) {
  const start = new Date(contest.startTime).getTime();
  const end = start + Math.max(0, contest.durationMinutes || 0) * 60_000;
  if (now >= start && now < end) {
    return { live: true, label: "正在进行" };
  }

  const diffMinutes = Math.max(0, Math.floor((start - now) / 60_000));
  if (diffMinutes < 60) {
    return { live: false, label: `${Math.max(1, diffMinutes)} 分钟后` };
  }
  if (diffMinutes < 24 * 60) {
    return { live: false, label: `${Math.floor(diffMinutes / 60)} 小时后` };
  }
  return { live: false, label: `${Math.floor(diffMinutes / (24 * 60))} 天后` };
}

function formatDuration(minutes) {
  if (!minutes) return "时长待定";
  const hours = Math.floor(minutes / 60);
  const remaining = minutes % 60;
  if (remaining === 0) return `${hours} 小时`;
  return `${hours} 小时 ${remaining} 分钟`;
}
