import { ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import { mondayOf, shiftWeek } from "../../utils/trainingWeek";

export default function WeekPicker({ report, loading, onChange, onReload }) {
  const start = report?.weekStart || "";
  const currentWeek = mondayOf(report?.today);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          aria-label="上一周"
          disabled={!start || loading}
          onClick={() => onChange(shiftWeek(start, -1))}
          className="rounded-xl border border-border bg-white p-2.5 text-text-primary disabled:opacity-40"
        >
          <ChevronLeft size={18} />
        </button>
        <label className="flex items-center gap-2 text-sm font-bold text-text-primary">
          选择周
          <input
            type="date"
            aria-label="选择训练周中的日期"
            value={start}
            max={report?.today || undefined}
            disabled={loading}
            onChange={(event) => {
              const next = mondayOf(event.target.value);
              if (next) onChange(next);
            }}
            className="min-h-10 rounded-xl border border-border bg-bg-secondary px-3 text-sm text-text-primary"
          />
        </label>
        <button
          type="button"
          aria-label="下一周"
          disabled={!start || loading || start >= currentWeek}
          onClick={() => onChange(shiftWeek(start, 1))}
          className="rounded-xl border border-border bg-white p-2.5 text-text-primary disabled:opacity-40"
        >
          <ChevronRight size={18} />
        </button>
        <button
          type="button"
          disabled={loading}
          onClick={() => onChange("")}
          className="min-h-10 rounded-xl px-3 text-sm font-bold text-[#245b8f] hover:bg-bg-secondary disabled:opacity-40"
        >
          本周
        </button>
      </div>
      <div className="flex items-center gap-3">
        {start && (
          <span className="text-xs text-text-secondary">
            {start} — {report.weekEnd} · 周一至周日
          </span>
        )}
        <button
          type="button"
          aria-label="刷新周训练记录"
          disabled={loading}
          onClick={onReload}
          className="rounded-xl border border-border p-2.5 text-text-primary disabled:opacity-40"
        >
          <RefreshCw size={17} className={loading ? "animate-spin" : ""} />
        </button>
      </div>
    </div>
  );
}
