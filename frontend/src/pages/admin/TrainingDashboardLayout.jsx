import { Suspense } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { CalendarCheck2, ClipboardCheck, Trophy, ChartNoAxesCombined } from "lucide-react";
import PageHeading from "../../components/ui/PageHeading";

const SECTIONS = [
  { to: "daily", label: "每日一题", icon: CalendarCheck2 },
  { to: "weekly", label: "每周一赛", icon: Trophy },
  { to: "records", label: "周训练记录", icon: ClipboardCheck },
];

export default function TrainingDashboardLayout() {
  return (
    <div className="stack-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <PageHeading icon={ChartNoAxesCombined} title="训练看板" tone="mint" />
        <nav aria-label="训练项目" className="training-sections inline-flex w-fit max-w-full flex-wrap gap-1 rounded-2xl border border-border p-1.5">
          {SECTIONS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end
              className="training-section-link inline-flex items-center justify-center gap-2 px-4 text-sm font-bold text-text-secondary"
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
