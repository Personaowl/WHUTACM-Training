import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { checkInToday, getDailyHistory, getTodayProblem } from "../api/dailyProblem";
import { updateMyProfile } from "../api/user";
import CheckInModal from "../components/ui/CheckInModal";
import EmptyState from "../components/ui/EmptyState";
import Pagination from "../components/ui/Pagination";
import ProblemCard from "../components/ui/ProblemCard";
import AddToProblemListButton from "../components/ui/AddToProblemListButton";
import { CardSkeleton } from "../components/ui/Skeleton";
import { useAuth } from "../context/AuthContext";
import { parseTags } from "../utils/cf";
import { CalendarCheck2, Settings2, Tags } from "lucide-react";
import PageHeading from "../components/ui/PageHeading";

const PAGE_SIZE = 10;
const TEXT = {
  title: "\u6bcf\u65e5\u4e00\u9898",
  loadTodayFailed: "\u83b7\u53d6\u4eca\u65e5\u9898\u5931\u8d25",
  loadDataFailed: "\u52a0\u8f7d\u6bcf\u65e5\u9898\u6570\u636e\u5931\u8d25",
  checkInFailed: "\u6821\u9a8c\u5931\u8d25",
  requestFailed: "\u8bf7\u6c42\u5931\u8d25",
  checkedIn: "\u5df2\u6253\u5361",
  submitCheckIn: "\u63d0\u4ea4\u6253\u5361",
  notGenerated: "\u4eca\u65e5\u9898\u76ee\u5c1a\u672a\u751f\u6210",
  history: "\u5386\u53f2\u8bb0\u5f55",
  noHistory: "\u6682\u65e0\u5386\u53f2\u8bb0\u5f55",
  checkedCount: "\u5df2\u6253\u5361",
  notCheckedIn: "\u672a\u6253\u5361",
  currentScore: "\u5f53\u524d\u5f97\u5206",
  completed: "\u4eca\u65e5\u5df2\u6253\u5361",
  problemsUnit: "\u9898",
  checkIn: "\u6253\u5361",
};

export default function DailyProblemPage() {
  const { user, updateUser } = useAuth();
  const [todayData, setTodayData] = useState(null);
  const [historyData, setHistoryData] = useState([]);
  const activeCheck = useRef(null);
  const groupedHistory = useMemo(() => groupByDate(historyData), [historyData]);
  const historyDates = useMemo(() => Object.keys(groupedHistory).sort().reverse(), [groupedHistory]);
  useEffect(() => () => activeCheck.current?.abort(), []);
  const [historyPage, setHistoryPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [checkInLoading, setCheckInLoading] = useState(false);
  const [checkInResult, setCheckInResult] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [showTags, setShowTags] = useState(user?.showProblemTags !== false);
  const [settingSaving, setSettingSaving] = useState(false);
  const [settingMessage, setSettingMessage] = useState("");

  const loadData = useCallback(async ({ showLoading = true } = {}) => {
    if (showLoading) setLoading(true);
    setError("");
    try {
      const [todayResp, historyResp] = await Promise.all([
        getTodayProblem(),
        getDailyHistory(0),
      ]);

      if (todayResp.code === 200) {
        setTodayData(todayResp.data);
      } else {
        setError(todayResp.message || TEXT.loadTodayFailed);
      }

      if (historyResp.code === 200) {
        setHistoryData(historyResp.data || []);
      }
    } catch {
      setError(TEXT.loadDataFailed);
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    setShowTags(user?.showProblemTags !== false);
  }, [user?.showProblemTags]);

  async function handleToggleTags() {
    if (!user?.id || settingSaving) return;
    const nextValue = !showTags;
    setSettingSaving(true);
    setSettingMessage("");
    try {
      const resp = await updateMyProfile(user.id, { showProblemTags: nextValue });
      if (resp.code === 200) {
        setShowTags(nextValue);
        updateUser(resp.data);
        setSettingMessage("设置已保存");
      } else {
        setSettingMessage(resp.message || "设置保存失败");
      }
    } catch {
      setSettingMessage("设置保存失败，请稍后重试");
    } finally {
      setSettingSaving(false);
    }
  }

  async function handleCheckIn(submissionId) {
    activeCheck.current?.abort();
    const controller = new AbortController();
    activeCheck.current = controller;
    const previousDayScore = Number(todayData?.score) || 0;
    setCheckInLoading(true);
    try {
      const resp = await checkInToday(submissionId, { signal: controller.signal });
      if (controller.signal.aborted || activeCheck.current !== controller) return;
      if (resp.code === 200) {
        const currentDayScore = Number(resp.data?.score) || 0;
        setCheckInResult({
          ...resp.data,
          earnedScore: Math.max(0, currentDayScore - previousDayScore),
        });
        void loadData({ showLoading: false });
      } else {
        setCheckInResult({
          accepted: false,
          submissionId,
          verdict: resp.message || TEXT.checkInFailed,
          score: 0,
        });
      }
    } catch {
      if (controller.signal.aborted || activeCheck.current !== controller) return;
      setCheckInResult({
        accepted: false,
        submissionId,
        verdict: TEXT.requestFailed,
        score: 0,
      });
    } finally {
      if (activeCheck.current === controller) {
        activeCheck.current = null;
        if (!controller.signal.aborted) setCheckInLoading(false);
      }
    }
  }

  function openCheckIn(slot) {
    setSelectedSlot(slot);
    setCheckInResult(null);
    setModalOpen(true);
  }

  function handleModalClose(open) {
    if (!open) {
      setModalOpen(false);
      setCheckInResult(null);
      setSelectedSlot(null);
    }
  }

  const todayProblems = todayData?.problems || [];

  if (loading) {
    return (
      <div className="stack-y-4">
        <PageHeading icon={CalendarCheck2} title={TEXT.title} />
        <CardSkeleton />
        <CardSkeleton />
      </div>
    );
  }

  const totalHistoryPages = Math.ceil(historyDates.length / PAGE_SIZE);
  const pagedDates = historyDates.slice(
    (historyPage - 1) * PAGE_SIZE,
    historyPage * PAGE_SIZE
  );

  const hasTodayContent = todayProblems.length > 0;
  const todayCompletedCount = todayProblems.filter((problem) => problem.checkedIn).length;
  const todayTotalCount = todayProblems.length;

  return (
    <div className="daily-page stack-y-5">
      <PageHeading icon={CalendarCheck2} title={TEXT.title} />

      {error && (
        <p className="text-sm text-error bg-[#fff0f0] rounded-ui px-3 py-2 m-0">{error}</p>
      )}

      <section className="bg-white border border-border rounded-ui px-4 py-3">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <span className="grid place-items-center w-9 h-9 shrink-0 rounded-full bg-bg-secondary">
              <Settings2 size={17} />
            </span>
            <div>
              <h2 className="text-sm font-bold text-text-primary m-0">每日一题设置</h2>
              <p className="text-xs text-text-secondary mt-0.5 mb-0">
                {showTags ? "当前会显示算法标签" : "当前已隐藏算法标签，避免提示解题方向"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            {settingMessage && (
              <span className="hidden sm:inline text-xs text-text-secondary">{settingMessage}</span>
            )}
            <span className="hidden sm:inline-flex items-center gap-1.5 text-xs text-text-secondary">
              <Tags size={14} />
              显示标签
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={showTags}
              aria-label="是否显示每日一题标签"
              disabled={settingSaving}
              onClick={handleToggleTags}
              className={`relative w-11 h-6 rounded-full border-0 transition-colors cursor-pointer disabled:opacity-50 ${
                showTags ? "bg-[#216e39]" : "bg-[#c8c8c2]"
              }`}
            >
              <span
                className={`absolute left-0.5 top-0.5 w-5 h-5 rounded-full bg-white shadow-xs transition-transform ${
                  showTags ? "translate-x-5" : "translate-x-0"
                }`}
              />
            </button>
          </div>
        </div>
      </section>

      {hasTodayContent ? (
        <div className="stack-y-3">
          {todayProblems.map((problem) => (
            <div key={problem.problemKey}>
              <ProblemCard
                problem={problem}
                showTags={showTags}
                actions={(
                  <>
                    <AddToProblemListButton problem={problem} />
                    <button
                      className="min-h-8 px-3 text-xs font-bold text-white bg-success hover:bg-[#268845] rounded-lg border-0 cursor-pointer disabled:opacity-40"
                      disabled={problem.checkedIn}
                      onClick={() => openCheckIn(problem)}
                    >
                      {problem.checkedIn ? TEXT.checkedIn : TEXT.submitCheckIn}
                    </button>
                  </>
                )}
              />
            </div>
          ))}

          {todayData?.checkedIn && (
            <p className="text-sm text-success m-0">
              {TEXT.completed} {todayCompletedCount}/{todayTotalCount} {TEXT.problemsUnit}
              {", "}
              {TEXT.currentScore}: {todayData.score ?? 0}
            </p>
          )}
        </div>
      ) : (
        <div className="bg-white border border-border rounded-ui p-8 text-center text-sm text-text-secondary">
          {TEXT.notGenerated}
        </div>
      )}

      <section>
        <div className="mb-3">
          <h2 className="text-base font-semibold text-text-primary m-0">{TEXT.history}</h2>
          <p className="text-xs text-text-secondary mt-1 mb-0">
            每日题目会自动保存，可按日期展开查看。
          </p>
        </div>
        {historyDates.length === 0 ? (
          <EmptyState title={TEXT.noHistory} />
        ) : (
          <div className="stack-y-1">
            {pagedDates.map((date) => {
              const items = groupedHistory[date];
              return (
                <details key={date} className="bg-white border border-border rounded-ui group">
                  <summary className="flex items-center justify-between px-4 py-2.5 cursor-pointer hover:bg-bg-secondary list-none">
                    <span className="text-sm font-medium text-text-primary">{date}</span>
                    <span className="text-xs text-text-secondary">
                      {items.filter((item) => item.checkedIn).length}/{items.length} {TEXT.checkedCount}
                    </span>
                  </summary>
                  <div className="border-t border-border">
                    {items.map((item, index) => (
                      <div
                        key={`${item.problemKey}-${index}`}
                        className="flex items-center justify-between px-4 py-2 text-sm"
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className={`text-xs px-1.5 py-0.5 rounded-sm ${
                              item.slot === "easy"
                                ? "bg-[#f0fff0] text-success"
                                : "bg-[#fff8f0] text-warning"
                            }`}
                          >
                            {item.slot ?? "?"}
                          </span>
                          <a
                            href={item.sourceUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-text-primary hover:underline"
                          >
                            {item.name}
                          </a>
                          <span className="text-text-secondary">({item.rating ?? "-"})</span>
                          {showTags && parseTags(item.tags).map((tag) => (
                            <span
                              key={tag}
                              className="text-[10px] text-text-secondary bg-bg-secondary px-1.5 py-0.5 rounded-full"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <AddToProblemListButton problem={item} compact />
                          {item.checkedIn ? (
                            <span className="text-success font-medium">
                              +{item.score ?? 0}
                            </span>
                          ) : (
                            <span className="text-text-secondary">{TEXT.notCheckedIn}</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </details>
              );
            })}
          </div>
        )}
        <Pagination
          page={historyPage}
          totalPages={totalHistoryPages}
          onPageChange={setHistoryPage}
        />
      </section>

      <CheckInModal
        open={modalOpen}
        onOpenChange={handleModalClose}
        onCheckIn={handleCheckIn}
        loading={checkInLoading}
        result={checkInResult}
        title={selectedSlot ? `${TEXT.checkIn} ${selectedSlot.name}` : TEXT.submitCheckIn}
      />
    </div>
  );
}

function groupByDate(historyItems) {
  const groups = {};
  for (const item of historyItems) {
    const date = item.date || "unknown";
    if (!groups[date]) groups[date] = [];
    groups[date].push(item);
  }
  return groups;
}
