import useWeeklyTraining from "../hooks/useWeeklyTraining";
import WeekPicker from "../components/training/WeekPicker";
import WeeklyTrainingDetails from "../components/training/WeeklyTrainingDetails";
import WeeklyExplanationDialog from "../components/training/WeeklyExplanationDialog";
import EmptyState from "../components/ui/EmptyState";
import { CalendarRange } from "lucide-react";
import PageHeading from "../components/ui/PageHeading";

export default function WeeklyTrainingPage() {
  const { report, loading, error, setWeek, reload, updateReport } =
    useWeeklyTraining();
  const member = report?.members?.[0];
  return (
    <div className="weekly-training-page stack-y-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <PageHeading icon={CalendarRange} title="我的周训练" tone="mint" />
        {!loading && !error && member?.canExplain && (
          <WeeklyExplanationDialog
            key={report.weekStart}
            week={report.weekStart}
            member={member}
            onUpdated={updateReport}
          />
        )}
      </header>
      <WeekPicker
        report={report}
        loading={loading}
        onChange={setWeek}
        onReload={reload}
      />
      {error && (
        <div
          role="alert"
          className="rounded-xl border border-[#e3c6c6] bg-[#fff5f5] p-4 text-sm text-error"
        >
          {error}
          <button type="button" onClick={reload} className="ml-3 underline">
            重试
          </button>
        </div>
      )}
      {loading ? (
        <div
          role="status"
          className="h-80 animate-pulse rounded-2xl border border-border bg-white"
        >
          <span className="sr-only">正在加载周训练记录</span>
        </div>
      ) : (
        !error &&
        (member ? (
          <div className="rounded-2xl border border-border bg-white p-4 shadow-xs sm:p-6">
            <WeeklyTrainingDetails member={member} />
          </div>
        ) : (
          <EmptyState
            title="所选周暂无现役训练记录"
          />
        ))
      )}
    </div>
  );
}
