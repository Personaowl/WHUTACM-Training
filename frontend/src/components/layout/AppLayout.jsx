import { useEffect, useRef, useState } from "react";
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
  const [smallViewport, setSmallViewport] = useState(() => window.innerWidth <= 1024);
  const sidebarRef = useRef(null);
  const frameRef = useRef(null);

  useEffect(() => {
    const query = window.matchMedia("(max-width: 1024px)");
    function updateViewport() { setSmallViewport(query.matches); }
    query.addEventListener("change", updateViewport);
    return () => query.removeEventListener("change", updateViewport);
  }, []);

  useEffect(() => {
    if (!mobileMenuOpen) return undefined;
    const previousFocus = document.activeElement;
    frameRef.current?.setAttribute("inert", "");
    const sidebar = sidebarRef.current;
    sidebar?.querySelector(".sidebar-mobile-close")?.focus();
    function handleKey(event) {
      if (event.key === "Escape") setMobileMenuOpen(false);
      if (event.key !== "Tab") return;
      const items = [...(sidebar?.querySelectorAll("a[href],button:not(:disabled)") || [])]
        .filter((item) => item.getClientRects().length > 0 && getComputedStyle(item).visibility !== "hidden");
      const first = items[0], last = items.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
    function handleResize() {
      if (window.innerWidth > 1024) setMobileMenuOpen(false);
    }
    document.addEventListener("keydown", handleKey);
    window.addEventListener("resize", handleResize);
    return () => {
      frameRef.current?.removeAttribute("inert");
      document.removeEventListener("keydown", handleKey);
      window.removeEventListener("resize", handleResize);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [mobileMenuOpen]);

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
      <Sidebar
        sidebarRef={sidebarRef}
        hiddenOnMobile={smallViewport && !mobileMenuOpen}
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
      <div className="app-frame" ref={frameRef}>
        <TopBar onMenuToggle={() => setMobileMenuOpen(true)} mobileMenuOpen={mobileMenuOpen} />
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
