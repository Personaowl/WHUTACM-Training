import { useCallback, useEffect, useState } from "react";
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
  const [isMobile, setIsMobile] = useState(() => window.matchMedia("(max-width: 980px)").matches);
  const closeMobileMenu = useCallback(() => setMobileMenuOpen(false), []);
  const mobileNavOpen = isMobile && mobileMenuOpen;

  useEffect(() => {
    const query = window.matchMedia("(max-width: 980px)");
    function handleViewportChange(event) {
      setIsMobile(event.matches);
      if (!event.matches) closeMobileMenu();
    }
    query.addEventListener("change", handleViewportChange);
    return () => query.removeEventListener("change", handleViewportChange);
  }, [closeMobileMenu]);

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
      <a className="skip-to-content" href="#main-content">跳到主要内容</a>
      <div className="ambient-grid" aria-hidden="true" />
      <Sidebar
        collapsed={sidebarCollapsed}
        mobileOpen={mobileNavOpen}
        isMobile={isMobile}
        onClose={closeMobileMenu}
        onToggle={toggleSidebar}
      />
      {mobileNavOpen && (
        <button
          className="sidebar-backdrop"
          onClick={closeMobileMenu}
          tabIndex={-1}
          aria-label="关闭导航"
        />
      )}
      <div className="app-frame" inert={mobileNavOpen ? "" : undefined}>
        <TopBar onMenuToggle={() => setMobileMenuOpen(true)} menuOpen={mobileNavOpen} />
        <main id="main-content" className="app-main" tabIndex={-1}>
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
