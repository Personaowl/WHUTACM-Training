import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getLeaderboard } from "../api/leaderboard";
import UserAvatar from "../components/ui/UserAvatar";
import Pagination from "../components/ui/Pagination";
import { ListSkeleton } from "../components/ui/Skeleton";
import EmptyState from "../components/ui/EmptyState";

const PAGE_SIZE = 50;

export default function LeaderboardPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const resp = await getLeaderboard(PAGE_SIZE, page);
        if (resp.code === 200) {
          const data = resp.data;
          if (!cancelled) {
            setItems(data.items || []);
            setTotal(data.total || 0);
          }
        } else {
          if (!cancelled) setError(resp.message || "获取排行榜失败");
        }
      } catch {
        if (!cancelled) setError("获取排行榜请求失败");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [page]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="stack-y-4">
      <h1 className="text-lg font-semibold text-text-primary m-0">排行榜</h1>
      <p className="text-sm text-text-secondary m-0">
        按总积分降序排列，积分相同时按最后打卡时间排序
      </p>

      {loading ? (
        <ListSkeleton rows={10} />
      ) : error ? (
        <div className="text-center py-12">
          <p className="text-error">{error}</p>
        </div>
      ) : items.length === 0 ? (
        <EmptyState title="暂无排行数据" />
      ) : (
        <div className="bg-white border border-border rounded-ui overflow-hidden">
          {items.map((item, idx) => (
            <Link
              key={item.userId}
              to={`/members/${item.userId}`}
              className={`flex items-center gap-3 px-4 py-3 hover:bg-bg-secondary transition-colors no-underline ${
                idx > 0 ? "border-t border-border" : ""
              }`}
            >
              <span className="text-sm text-text-secondary w-7 text-right font-mono shrink-0">
                {(page - 1) * PAGE_SIZE + idx + 1}
              </span>
              <UserAvatar
                user={{
                  username: item.displayName || item.username,
                  avatarUrl: item.avatarUrl,
                }}
                size={32}
              />
              <span className="flex-1 text-sm text-text-primary font-medium truncate">
                {item.displayName || item.username}
              </span>
              <span className="text-sm font-semibold text-text-primary shrink-0">
                {item.totalPoints ?? 0} 分
              </span>
            </Link>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </div>
  );
}
