import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getHomeOverview } from "../api/home";
import { getHeatmap } from "../api/user";
import { useAuth } from "../context/AuthContext";
import ProblemCard from "../components/ui/ProblemCard";
import AddToProblemListButton from "../components/ui/AddToProblemListButton";
import Heatmap from "../components/ui/Heatmap";
import UserAvatar from "../components/ui/UserAvatar";
import { CardSkeleton } from "../components/ui/Skeleton";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  CalendarCheck2,
  CalendarRange,
  ChartNoAxesCombined,
  Code2,
  Crosshair,
  LoaderCircle,
  LibraryBig,
  Medal,
  NotebookPen,
  Send,
  Trophy,
} from "lucide-react";
import CodeforcesOverview from "../components/profile/CodeforcesOverview";
import { PageIcon } from "../components/ui/PageHeading";
import { getCurrentUserPoints } from "../utils/home.js";
import "./HomePage.css";

export default function HomePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [heatmapData, setHeatmapData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [heatmapLoading, setHeatmapLoading] = useState(true);
  const [error, setError] = useState("");
  const [heatmapError, setHeatmapError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setLoading(true);
    setHeatmapLoading(Boolean(user?.id));
    setError("");
    setHeatmapError("");
    setHeatmapData([]);

    getHomeOverview(10)
      .then((overview) => {
        if (!cancelled) setData(overview);
      })
      .catch(() => {
        if (!cancelled) setError("加载首页数据失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    if (user?.id) {
      getHeatmap(user.id, 365)
        .then((heatmap) => {
          if (!cancelled && heatmap?.code === 200) {
            setHeatmapData(heatmap.data || []);
          } else if (!cancelled) {
            setHeatmapError("热力图暂时无法加载");
          }
        })
        .catch(() => {
          if (!cancelled) setHeatmapError("热力图暂时无法加载");
        })
        .finally(() => {
          if (!cancelled) setHeatmapLoading(false);
        });

    } else {
      setHeatmapData([]);
      setHeatmapLoading(false);
    }

    return () => { cancelled = true; };
  }, [user?.id, reloadKey]);

  useEffect(() => {
    if (!data?.problemPoolInitializing) return undefined;

    let requesting = false;
    let cancelled = false;
    const timer = window.setInterval(async () => {
      if (requesting || document.visibilityState !== "visible") return;
      requesting = true;
      try {
        const overview = await getHomeOverview(10);
        if (!cancelled) {
          setData(overview);
          setError("");
        }
      } catch {
        // Keep the current overview visible and retry while the pool is initializing.
      } finally {
        requesting = false;
      }
    }, 3000);

    return () => { cancelled = true; window.clearInterval(timer); };
  }, [data?.problemPoolInitializing, user?.id]);

  const { totalUsers, topUsers, todayProblem, dailySubmissionSummary } = data || {};
  const myPoints = getCurrentUserPoints(data, user?.id);

  const todayProblems = Array.isArray(todayProblem) ? todayProblem : (todayProblem ? [todayProblem] : []);

  return (
    <div className="home-page">
      <header className="home-profile-panel">
        <div className="home-profile-identity">
          <UserAvatar user={user} size={76} />
          <div>
            <h1>{user?.displayName || user?.username || "训练者"}</h1>
            <p title={user?.username}>@{user?.username || "guest"}</p>
          </div>
        </div>

        <div className="home-quick-actions">
          <Link to="/problem-lists" className="home-secondary-entry">
            <BookOpen size={17} aria-hidden="true" />我的题单
          </Link>
          <Link to="/daily" className="home-training-entry">
            <span>开始今日训练</span><ArrowUpRight size={18} aria-hidden="true" />
          </Link>
        </div>

        <dl className="home-stats" aria-label="训练概览" aria-busy={loading}>
          <StatCard icon={Trophy} label="我的积分" value={myPoints} />
          <StatCard icon={Send} label="今日提交" value={data ? dailySubmissionSummary?.todaySubmissions ?? 0 : null} />
          <StatCard icon={Activity} label="近 7 日活跃" value={data ? totalUsers ?? 0 : null} />
        </dl>
      </header>

      {error && (
        <div className="home-load-error" role="alert">
          <p>{error}，请稍后重试。</p>
          <button type="button" className="button-ghost" onClick={() => setReloadKey((key) => key + 1)}>重新加载</button>
        </div>
      )}

      <div className="home-training-grid">
        <section className="home-section home-daily-section" aria-labelledby="home-daily-title">
          <SectionHeading id="home-daily-title" title="今日训练" icon={CalendarCheck2} to="/daily" linkText="查看每日一题" />
          {loading ? <div aria-label="正在加载今日题目"><CardSkeleton /><CardSkeleton /></div> : error ? (
            <div className="home-empty-state">今日题目暂时无法加载</div>
          ) : todayProblems.length > 0 ? (
            <div className="home-problem-grid">
              {todayProblems.map((problem, index) => (
                <ProblemCard
                  key={problem.problemKey || index}
                  problem={problem}
                  actions={<AddToProblemListButton problem={problem} />}
                />
              ))}
            </div>
          ) : (
            <div className="home-empty-state" role="status">
              {data?.problemPoolInitializing ? (
                <><LoaderCircle className="animate-spin" size={22} aria-hidden="true" /><p>正在初始化题库</p></>
              ) : (
                <><Code2 size={26} aria-hidden="true" /><p>今日题目尚未生成</p><Link to="/practice">先去自主练习 <ArrowRight size={15} aria-hidden="true" /></Link></>
              )}
            </div>
          )}
        </section>

        <aside className="home-training-aside" aria-label="个人训练概览">
          {user?.codeforcesHandle ? (
            <CodeforcesOverview userId={user?.id} handle={user.codeforcesHandle} compact />
          ) : (
            <section className="home-binding-prompt">
              <img src="/platforms/codeforces.png" alt="" width="32" height="32" />
              <div><h2>连接 Codeforces</h2><p>查看你的 Rating 和做题数据</p></div>
              <Link to="/account-binding" className="home-text-link">绑定账号 <ArrowUpRight size={15} aria-hidden="true" /></Link>
            </section>
          )}
          <nav className="home-shortcuts" aria-label="训练快捷入口">
            <Link to="/upsolve"><NotebookPen size={19} aria-hidden="true" /><span>补题计划</span><ArrowUpRight size={16} aria-hidden="true" /></Link>
            <Link to="/weekly-training"><CalendarRange size={19} aria-hidden="true" /><span>我的周训练</span><ArrowUpRight size={16} aria-hidden="true" /></Link>
            <Link to="/practice"><Crosshair size={19} aria-hidden="true" /><span>自主练习</span><ArrowUpRight size={16} aria-hidden="true" /></Link>
          </nav>
        </aside>
      </div>

      <section className="render-lazy cf-heatmap-section">
        <SectionHeading title="训练足迹" icon={ChartNoAxesCombined} tone="mint" subtitle="过去 12 个月" />
        {heatmapLoading ? (
          <CardSkeleton />
        ) : heatmapError ? (
          <div className="bg-white border border-border rounded-ui p-6 text-sm text-text-secondary text-center">
            {heatmapError}
          </div>
        ) : (
          <div className="cf-heatmap-card">
            <Heatmap data={heatmapData} totalAllTime={myPoints} />
          </div>
        )}
      </section>

      <div className="render-lazy home-community-grid">
          <section className="home-section home-shared-section">
            <SectionHeading title="题单" icon={LibraryBig} tone="violet" />
            <nav className="home-library-links" aria-label="题单入口">
              <Link to="/problem-lists"><BookOpen size={19} aria-hidden="true" /><span>我的题单</span><ArrowUpRight size={16} aria-hidden="true" /></Link>
              <Link to="/shared-problem-lists"><LibraryBig size={19} aria-hidden="true" /><span>共享题单</span><ArrowUpRight size={16} aria-hidden="true" /></Link>
            </nav>
          </section>

          <section className="home-section home-leaderboard-section">
            <SectionHeading title="训练排行" icon={Medal} tone="amber" to="/leaderboard" linkText="完整排行" />
            {loading ? <CardSkeleton /> : error ? <div className="home-empty-state">排行暂时无法加载</div> : topUsers && topUsers.length > 0 ? (
              <ol className="home-leaderboard">
                {topUsers.map((item, idx) => (
                  <li key={item.userId ?? item.id}>
                  <Link
                    to={`/members/${item.userId ?? item.id}`}
                    className="home-leaderboard-row"
                  >
                    <span className={`home-rank ${idx < 3 ? "is-top" : ""}`}>
                      {String(idx + 1).padStart(2, "0")}
                    </span>
                    <UserAvatar
                      user={{
                        username: item.displayName || item.username,
                        avatarUrl: item.avatarUrl,
                      }}
                      size={32}
                    />
                    <span className="home-leaderboard-name">
                      {item.displayName || item.username}
                    </span>
                    <strong>{formatNumber(item.totalPoints)}<small>分</small></strong>
                  </Link>
                  </li>
                ))}
              </ol>
            ) : (
              <div className="home-empty-state">
                暂无排行数据
              </div>
            )}
          </section>
      </div>
    </div>
  );
}

function StatCard({ icon: Icon, label, value }) {
  return (
    <div className="home-stat">
      <dt><span className="home-stat-icon"><Icon size={19} strokeWidth={1.7} aria-hidden="true" /></span>{label}</dt>
      <dd>{formatNumber(value)}</dd>
    </div>
  );
}

function SectionHeading({ id, title, subtitle, to, linkText, icon, tone }) {
  return (
    <div className="home-section-heading">
      <div className="home-section-title">{icon && <PageIcon icon={icon} tone={tone} compact />}<h2 id={id}>{title}</h2></div>
      {to ? <Link to={to}>{linkText}<ArrowUpRight size={15} aria-hidden="true" /></Link> : <span>{subtitle}</span>}
    </div>
  );
}

function formatNumber(value) {
  return value == null ? "—" : Number(value).toLocaleString("zh-CN");
}
