import { Suspense } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { CalendarDays, ClipboardCheck, Trophy } from "lucide-react";

const SECTIONS = [
  { to: "daily", label: "每日一题", icon: CalendarDays },
  { to: "weekly", label: "每周一赛", icon: Trophy },
  { to: "records", label: "周训练记录", icon: ClipboardCheck },
];

export default function TrainingDashboardLayout() {
  return (
    <div className="stack-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="m-0 text-2xl font-bold tracking-tight text-text-primary">训练看板</h1>
        <nav aria-label="训练项目" className="inline-flex w-fit max-w-full flex-wrap gap-1 rounded-2xl border border-border bg-white p-1.5 shadow-xs">
          {SECTIONS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end
              className={({ isActive }) => `inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 text-sm font-bold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#245b8f] ${
                isActive ? "bg-[#142943] text-white shadow-xs" : "text-text-secondary hover:bg-bg-secondary hover:text-text-primary"
              }`}
            >
              <Icon size={17} aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
      </header>
      <Suspense fallback={
        <div className="flex min-h-64 items-center justify-center" role="status">
          <span className="route-loader" aria-hidden="true" />
          <span className="sr-only">训练数据加载中</span>
        </div>
      }>
        <Outlet />
      </Suspense>
    </div>
  );
}
