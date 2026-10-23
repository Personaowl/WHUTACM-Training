import * as Dropdown from "@radix-ui/react-dropdown-menu";
import { ChevronDown, Link2, LogOut, Menu, UserRound } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import UserAvatar from "./UserAvatar";

const PAGE_NAMES = {
  "/": "首页",
  "/daily": "每日一题",
  "/weekly-training": "我的周训练",
  "/contests": "近期比赛",
  "/leaderboard": "排行榜",
  "/practice": "自主练习",
  "/problem-lists": "我的题单",
  "/shared-problem-lists": "共享题单",
  "/profile": "个人中心",
  "/account-binding": "账号绑定",
  "/admin/training": "训练看板",
  "/admin/training/daily": "训练看板 / 每日一题",
  "/admin/training/weekly": "训练看板 / 每周一赛",
  "/admin/training/records": "训练看板 / 周训练记录",
  "/admin/daily": "题目管理",
  "/admin/users": "用户管理",
};

export default function TopBar({ onMenuToggle }) {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const pageName = location.pathname.startsWith("/members/")
    ? "成员主页"
    : PAGE_NAMES[location.pathname] || "WHUT-ACM";

  return (
    <header className="app-topbar">
      <div className="topbar-title">
        <button className="topbar-menu" onClick={onMenuToggle} aria-label="打开导航">
          <Menu size={20} />
        </button>
        <span className="topbar-route">/ {pageName}</span>
        <span className="topbar-context">训练工作台</span>
      </div>

      <div className="topbar-actions">
        <span className="topbar-date">
          {new Intl.DateTimeFormat("zh-CN", {
            month: "2-digit",
            day: "2-digit",
            weekday: "short",
          }).format(new Date())}
        </span>
        {isAuthenticated && user ? (
          <Dropdown.Root>
            <Dropdown.Trigger asChild>
              <button className="user-menu-trigger">
                <UserAvatar user={user} size={30} />
                <span className="user-menu-copy">
                  <strong>{user.displayName || user.username || ""}</strong>
                  <small>@{user.username}</small>
                </span>
                <ChevronDown size={14} />
              </button>
            </Dropdown.Trigger>
            <Dropdown.Portal>
              <Dropdown.Content
                className="dropdown-content"
                sideOffset={8}
                align="end"
              >
                <Dropdown.Item className="dropdown-item" onClick={() => navigate("/profile")}>
                  <UserRound size={16} />个人资料
                </Dropdown.Item>
                <Dropdown.Item className="dropdown-item" onClick={() => navigate("/account-binding")}>
                  <Link2 size={16} />账号绑定
                </Dropdown.Item>
                <Dropdown.Separator className="dropdown-separator" />
                <Dropdown.Item className="dropdown-item" onClick={() => logout()}>
                  <LogOut size={16} />退出登录
                </Dropdown.Item>
              </Dropdown.Content>
            </Dropdown.Portal>
          </Dropdown.Root>
        ) : (
          <div className="topbar-auth-actions">
            <button onClick={() => navigate("/login")} className="button-ghost">登录</button>
            <button onClick={() => navigate("/register")} className="button-primary">注册</button>
          </div>
        )}
      </div>
    </header>
  );
}
