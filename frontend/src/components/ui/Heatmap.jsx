import { memo, useMemo } from "react";

const LEVEL_COLORS = ["#edf0f2", "#9be7ad", "#45c96a", "#26964d", "#176b38"];
const CELL_SIZE = 20;
const CELL_GAP = 3;
const WEEK_WIDTH = CELL_SIZE + CELL_GAP;
const DAY_LABEL_WIDTH = 44;
const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_NAMES = ["", "Mon", "", "Wed", "", "Fri", ""];

function Heatmap({ data = [], totalAllTime = 0 }) {
  const model = useMemo(() => buildHeatmap(data), [data]);

  return (
    <div className="heatmap-frame heatmap-cf-layout">
      <div className="heatmap-toolbar">
        <span>Training activity</span>
        <div className="heatmap-legend" aria-label="Activity intensity">
          <span>Less</span>
          {LEVEL_COLORS.map((color) => <i key={color} style={{ background: color }} />)}
          <span>More</span>
        </div>
      </div>

      <div className="heatmap-scroll">
        <div className="heatmap-canvas" style={{ minWidth: DAY_LABEL_WIDTH + model.weeks.length * WEEK_WIDTH }}>
          <div className="heatmap-months" style={{ gap: CELL_GAP }}>
            <div style={{ width: DAY_LABEL_WIDTH, flexShrink: 0 }} />
            {model.monthSpans.map((span, index) => (
              <span key={`${span.month}-${index}`} style={{ width: span.weeks * WEEK_WIDTH - CELL_GAP }}>
                {MONTH_NAMES[span.month]}
              </span>
            ))}
          </div>

          <div className="heatmap-body" role="grid" aria-label="Training activity over the last year">
            <div className="heatmap-days" style={{ width: DAY_LABEL_WIDTH, gap: CELL_GAP, paddingRight: CELL_GAP }}>
              {DAY_NAMES.map((day, index) => (
                <span key={index} style={{ lineHeight: `${CELL_SIZE}px`, height: CELL_SIZE }}>{day}</span>
              ))}
            </div>
            <div className="heatmap-weeks" style={{ gap: CELL_GAP }}>
              {model.weeks.map((week, weekIndex) => (
                <div key={weekIndex} className="heatmap-week">
                  {week.map((day, dayIndex) => (
                    <span
                      key={dayIndex}
                      className={`heatmap-cell ${day ? "" : "is-empty"}`}
                      style={{
                        width: CELL_SIZE,
                        height: CELL_SIZE,
                        marginBottom: dayIndex === 6 ? 0 : CELL_GAP,
                        background: day ? LEVEL_COLORS[day.level] : "transparent",
                      }}
                      title={day ? `${day.date}: ${day.score} points` : ""}
                      aria-label={day ? `${day.date}, ${day.score} points` : undefined}
                      role={day ? "gridcell" : undefined}
                    />
                  ))}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="heatmap-cf-stats">
        <HeatmapStat value={totalAllTime} unit="points" label="earned for all time" />
        <HeatmapStat value={model.totalScore} unit="points" label="earned for the last year" />
        <HeatmapStat value={model.lastMonthScore} unit="points" label="earned for the last month" />
        <HeatmapStat value={model.activeDays} unit="days" label="active for the last year" />
        <HeatmapStat value={model.longestStreak} unit="days" label="longest streak for the last year" />
        <HeatmapStat value={model.lastMonthLongestStreak} unit="days" label="longest streak for the last month" />
      </div>
    </div>
  );
}

function HeatmapStat({ value, unit, label }) {
  return (
    <div className="heatmap-cf-stat">
      <strong>{value == null ? "—" : Number(value || 0).toLocaleString()} <span>{unit}</span></strong>
      <small>{label}</small>
    </div>
  );
}

function buildHeatmap(data) {
  const dateMap = new Map(data.map((item) => [item.date, item]));
  const today = startOfDay(new Date());
  const startDate = new Date(today);
  startDate.setDate(startDate.getDate() - 364);
  startDate.setDate(startDate.getDate() - startDate.getDay());
  const alignedStart = new Date(startDate);
  const weeks = [];
  const current = new Date(startDate);

  while (current <= today) {
    const week = [];
    for (let dayIndex = 0; dayIndex < 7 && current <= today; dayIndex += 1) {
      const date = formatDate(current);
      const item = dateMap.get(date);
      const score = Number(item?.score) || 0;
      let level = Math.max(0, Math.min(4, Number(item?.colorLevel) || 0));
      if (score > 0 && level === 0) level = 1;
      week.push({ date, level, score });
      current.setDate(current.getDate() + 1);
    }
    while (week.length < 7) week.push(null);
    weeks.push(week);
  }

  const monthSpans = [];
  for (let weekIndex = 0; weekIndex < weeks.length;) {
    const midDate = new Date(alignedStart);
    midDate.setDate(midDate.getDate() + weekIndex * 7 + 3);
    const month = midDate.getMonth();
    let count = 0;
    while (weekIndex + count < weeks.length) {
      const checkDate = new Date(alignedStart);
      checkDate.setDate(checkDate.getDate() + (weekIndex + count) * 7 + 3);
      if (checkDate.getMonth() !== month) break;
      count += 1;
    }
    monthSpans.push({ month, weeks: Math.max(count, 1) });
    weekIndex += Math.max(count, 1);
  }

  const activeItems = data.filter((item) => (Number(item.score) || 0) > 0);
  const monthStart = new Date(today);
  monthStart.setDate(monthStart.getDate() - 29);
  const lastMonthItems = activeItems.filter((item) => parseDate(item.date) >= monthStart);
  return {
    weeks,
    monthSpans,
    activeDays: activeItems.length,
    totalScore: data.reduce((sum, item) => sum + (Number(item.score) || 0), 0),
    lastMonthScore: lastMonthItems.reduce((sum, item) => sum + (Number(item.score) || 0), 0),
    longestStreak: longestStreak(activeItems),
    lastMonthLongestStreak: longestStreak(lastMonthItems),
  };
}

function longestStreak(items) {
  const dates = items.map((item) => parseDate(item.date)).sort((left, right) => left - right);
  let longest = 0;
  let current = 0;
  let previous = null;
  dates.forEach((date) => {
    const difference = previous ? Math.round((date - previous) / 86400000) : null;
    current = difference === 1 ? current + 1 : 1;
    longest = Math.max(longest, current);
    previous = date;
  });
  return longest;
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function parseDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export default memo(Heatmap);
