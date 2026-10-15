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
  CalendarDays,
  Code2,
  LoaderCircle,
  Send,
  Trophy,
} from "lucide-react";
import CodeforcesOverview from "../components/profile/CodeforcesOverview";
import "./HomePage.css";

export default function HomePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [heatmapData, setHeatmapData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [heatmapLoading, setHeatmapLoading] = useState(true);
  const [error, setError] = useState("");
  const [heatmapError, setHeatmapError] = useState("");

  useEffect(() => {
    let cancelled = false;
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
  }, [user?.id]);

  useEffect(() => {
    if (!data?.problemPoolInitializing) return undefined;

    let requesting = false;
    const timer = window.setInterval(async () => {
      if (requesting) return;
      requesting = true;
      try {
        const overview = await getHomeOverview(10);
        setData(overview);
        setError("");
      } catch {
        // Keep the current overview visible and retry while the pool is initializing.
      } finally {
        requesting = false;
      }
    }, 3000);

    return () => window.clearInterval(timer);
  }, [data?.problemPoolInitializing]);

  if (loading) {
    return (
      <div className="stack-y-4">
        <h1 className="text-lg font-semibold">首页</h1>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-error">{error}</p>
      </div>
    );
  }

  const { totalUsers, topUsers, todayProblem, todayPushProblem, dailySubmissionSummary } = data || {};

  const todayProblems = Array.isArray(todayProblem) ? todayProblem : (todayProblem ? [todayProblem] : []);

  return (
    <div className="home-page">
      <header className="home-profile-panel">
        <div className="home-profile-identity">
          <UserAvatar user={user} size={76} />
          <div>
            <span className="home-profile-eyebrow">我的训练空间</span>
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

        <dl className="home-stats" aria-label="训练概览">
          <StatCard icon={Trophy} label="我的积分" value={user?.totalPoints ?? 0} />
          <StatCard icon={Send} label="今日提交" value={dailySubmissionSummary?.todaySubmissions ?? 0} />
          <StatCard icon={Activity} label="近 7 日活跃" value={totalUsers ?? 0} />
        </dl>
      </header>

      <div className="home-training-grid">
        <section className="home-section home-daily-section" aria-labelledby="home-daily-title">
          <SectionHeading id="home-daily-title" title="今日训练" to="/daily" linkText="查看每日一题" />
          {todayProblems.length > 0 ? (
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
                <><LoaderCircle className="animate-spin" size={22} aria-hidden="true" /><p>题库初始化中，今日题目即将就绪</p></>
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
            <Link to="/weekly-training"><CalendarDays size={19} aria-hidden="true" /><span>我的周训练<small>回顾本周完成情况</small></span><ArrowUpRight size={16} aria-hidden="true" /></Link>
            <Link to="/practice"><Code2 size={19} aria-hidden="true" /><span>自主练习<small>按难度挑选下一题</small></span><ArrowUpRight size={16} aria-hidden="true" /></Link>
          </nav>
        </aside>
      </div>

      <section className="render-lazy cf-heatmap-section">
        <SectionHeading title="训练足迹" subtitle="过去 12 个月" />
        {heatmapLoading ? (
          <CardSkeleton />
        ) : heatmapError ? (
          <div className="bg-white border border-border rounded-ui p-6 text-sm text-text-secondary text-center">
            {heatmapError}
          </div>
        ) : (
          <div className="cf-heatmap-card">
            <Heatmap data={heatmapData} totalAllTime={user?.totalPoints ?? 0} />
          </div>
        )}
      </section>

      <div className="render-lazy home-community-grid">
          <section className="home-section home-push-section">
            <SectionHeading title="今日推题" to="/push" linkText="更多推题" />
            {todayPushProblem ? (
              <div className="home-push-content">
                <span className="home-content-label"><Send size={15} aria-hidden="true" /> 来自训练伙伴的推荐</span>
                <h3>
                  {todayPushProblem.title}
                </h3>
                {todayPushProblem.description ? (
                  <p>
                    {todayPushProblem.description}
                  </p>
                ) : null}
                {todayPushProblem.link && (
                  <a
                    href={todayPushProblem.link}
                    target="_blank"
                    rel="noreferrer"
                    className="home-text-link"
                  >
                    打开题目 <ArrowUpRight size={15} aria-hidden="true" />
                  </a>
                )}
              </div>
            ) : (
              <div className="home-empty-state">
                <Send size={26} aria-hidden="true" /><p>今天还没有推荐题目</p>
                <Link to="/push">分享一道好题 <ArrowRight size={15} aria-hidden="true" /></Link>
              </div>
            )}
          </section>

          <section className="home-section home-leaderboard-section">
            <SectionHeading title="训练排行" to="/leaderboard" linkText="完整排行" />
            {topUsers && topUsers.length > 0 ? (
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

function SectionHeading({ id, title, subtitle, to, linkText }) {
  return (
    <div className="home-section-heading">
      <h2 id={id}>{title}</h2>
      {to ? <Link to={to}>{linkText}<ArrowUpRight size={15} aria-hidden="true" /></Link> : <span>{subtitle}</span>}
    </div>
  );
}

function formatNumber(value) {
  return Number(value ?? 0).toLocaleString("zh-CN");
}
