import {
  CalendarDays,
  CalendarClock,
  ClipboardCheck,
  ChartNoAxesCombined,
  Code2,
  FolderOpen,
  House,
  Globe2,
  Settings2,
  Trophy,
  UserRound,
  Users,
  PanelLeftClose,
  PanelLeftOpen,
  Link2,
  X,
} from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const NAV_ITEMS = [
  { to: "/", label: "首页", icon: House },
  { to: "/daily", label: "每日一题", icon: CalendarDays },
  { to: "/weekly-training", label: "我的周训练", icon: ClipboardCheck, requireAuth: true },
  { to: "/contests", label: "近期比赛", icon: CalendarClock },
  { to: "/leaderboard", label: "排行榜", icon: Trophy },
  { to: "/practice", label: "自主练习", icon: Code2 },
  { to: "/problem-lists", label: "我的题单", icon: FolderOpen },
  { to: "/shared-problem-lists", label: "共享题单", icon: Globe2 },
  { to: "/profile", label: "个人中心", icon: UserRound, requireAuth: true },
  { to: "/account-binding", label: "账号绑定", icon: Link2, requireAuth: true },
];

const ADMIN_ITEMS = [
  { to: "/admin/training", label: "训练看板", icon: ChartNoAxesCombined },
  { to: "/admin/daily", label: "题管理", icon: Settings2 },
  { to: "/admin/users", label: "用户管理", icon: Users },
];

function NavLink({ item, active, collapsed, onNavigate }) {
  const Icon = item.icon;
  return (
    <Link
      to={item.to}
      onClick={onNavigate}
      className={`sidebar-link ${active ? "is-active" : ""}`}
      aria-label={item.label}
      title={collapsed ? item.label : undefined}
    >
      <Icon size={19} strokeWidth={1.8} />
      <span className="sidebar-label">{item.label}</span>
      {active && <span className="sidebar-active-dot" aria-hidden="true" />}
    </Link>
  );
}

export default function Sidebar({
  collapsed,
  mobileOpen,
  onClose,
  onToggle,
}) {
  const { isAuthenticated, isAdmin } = useAuth();
  const location = useLocation();
  const filteredItems = NAV_ITEMS.filter(
    (item) => !item.requireAuth || isAuthenticated
  );

  return (
    <aside
      className={`app-sidebar ${collapsed ? "is-collapsed" : ""} ${
        mobileOpen ? "is-mobile-open" : ""
      }`}
    >
      <div className="sidebar-brand">
        <Link to="/" className="sidebar-brand-link" onClick={onClose}>
          <span className="sidebar-brand-mark">
            <img src="/whut-acm-logo.png" alt="" aria-hidden="true" />
          </span>
          <span className="sidebar-brand-copy">
            <strong>WHUT-ACM</strong>
            <small>训练平台</small>
          </span>
        </Link>
        <button className="sidebar-mobile-close" onClick={onClose} aria-label="关闭菜单">
          <X size={19} />
        </button>
      </div>

      <nav className="sidebar-nav" aria-label="主导航">
        {filteredItems.map((item) => (
          <NavLink
            key={item.to}
            item={item}
            active={location.pathname === item.to}
            collapsed={collapsed}
            onNavigate={onClose}
          />
        ))}

        {isAdmin && (
          <div className="sidebar-admin">
            {ADMIN_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                item={item}
                active={location.pathname === item.to || location.pathname.startsWith(`${item.to}/`)}
                collapsed={collapsed}
                onNavigate={onClose}
              />
            ))}
          </div>
        )}
      </nav>

      <div className="sidebar-footer">
        <button
          type="button"
          className="sidebar-toggle"
          onClick={onToggle}
          aria-label={collapsed ? "展开侧边栏" : "收起侧边栏"}
          title={collapsed ? "展开侧边栏" : "收起侧边栏"}
        >
          {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
          <span className="sidebar-label">{collapsed ? "展开侧边栏" : "收起侧边栏"}</span>
        </button>
      </div>
    </aside>
  );
}
