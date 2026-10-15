import { useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "../ui/Sidebar";
import TopBar from "../ui/TopBar";
import DevPanel from "../ui/DevPanel";
import { useAuth } from "../../context/AuthContext";
import { checkDevStatus } from "../../api/dev";

const IS_VITE_DEV = import.meta.env.DEV;
const SIDEBAR_STORAGE_KEY = "whut-acm:sidebar-collapsed";

export default function AppLayout() {
  const { isDevBackend, setDevMode } = useAuth();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => (
    window.localStorage.getItem(SIDEBAR_STORAGE_KEY) !== "false"
  ));
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  function toggleSidebar() {
    setSidebarCollapsed((current) => {
      const next = !current;
      window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
      return next;
    });
  }

  // 仅 Vite dev 模式下尝试探测后端 profile，避免 prod 产生无意义的 404 请求
  useEffect(() => {
    if (!IS_VITE_DEV) return;
    let cancelled = false;
    checkDevStatus()
      .then((resp) => {
        if (!cancelled) {
          setDevMode(resp?.code === 200 && resp?.data?.active === true);
        }
      })
      .catch(() => {
        // 调试能力探测失败不影响正常页面。
      });
    return () => { cancelled = true; };
  }, [setDevMode]);

  return (
    <div className="app-shell">
      <div className="ambient-grid" aria-hidden="true" />
      <Sidebar
        collapsed={sidebarCollapsed}
        mobileOpen={mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        onToggle={toggleSidebar}
      />
      {mobileMenuOpen && (
        <button
          className="sidebar-backdrop"
          onClick={() => setMobileMenuOpen(false)}
          aria-label="关闭导航"
        />
      )}
      <div className="app-frame">
        <TopBar onMenuToggle={() => setMobileMenuOpen(true)} />
        <main className="app-main">
          <div className="app-content">
            <Outlet />
            {isDevBackend && (
              <div className="mt-5">
                <DevPanel />
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
