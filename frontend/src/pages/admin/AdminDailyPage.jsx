import { useEffect, useState } from "react";
import { getTodayProblem } from "../../api/dailyProblem";
import { regenerateDaily, redrawSlot } from "../../api/admin";
import ProblemCard from "../../components/ui/ProblemCard";
import { CardSkeleton } from "../../components/ui/Skeleton";
import { Settings2 } from "lucide-react";
import PageHeading from "../../components/ui/PageHeading";

export default function AdminDailyPage() {
  const [todayData, setTodayData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [redrawing, setRedrawing] = useState(null);

  async function load() {
    setLoading(true);
    try {
      const resp = await getTodayProblem();
      if (resp.code === 200) setTodayData(resp.data);
      else setMessage(resp.message || "获取失败");
    } catch {
      setMessage("请求失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function handleRegenerate() {
    setMessage("");
    try {
      const resp = await regenerateDaily();
      if (resp.code === 200) {
        setMessage("已重生成今日题");
        load();
      } else {
        setMessage(resp.message || "操作失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleRedraw(slot) {
    setRedrawing(slot);
    setMessage("");
    try {
      const resp = await redrawSlot(null, slot);
      if (resp.code === 200) {
        setMessage(`已重抽 ${slot} 题`);
        load();
      } else {
        setMessage(resp.message || "操作失败");
      }
    } catch {
      setMessage("请求失败");
    } finally {
      setRedrawing(null);
    }
  }

  return (
    <div className="stack-y-4">
      <PageHeading icon={Settings2} title="每日题管理" tone="violet" />

      {message && (
        <p className="text-sm text-text-secondary bg-bg-secondary rounded-ui px-3 py-2 m-0">{message}</p>
      )}

      {loading ? (
        <CardSkeleton />
      ) : todayData?.problems && todayData.problems.length > 0 ? (
        <div className="stack-y-3">
          {todayData.problems.map((p) => (
            <div key={p.problemKey}>
              <ProblemCard problem={p} />
              <div className="mt-1.5 flex justify-end">
                <button
                  className="px-3 py-1 text-xs border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer disabled:opacity-50"
                  disabled={redrawing === p.type?.toLowerCase()}
                  onClick={() => handleRedraw(p.type?.toLowerCase() || "easy")}
                >
                  {redrawing === p.type?.toLowerCase() ? "重抽中..." : `重抽 ${p.type || ""}`}
                </button>
              </div>
            </div>
          ))}
          <div className="flex justify-center pt-2">
            <button
              className="px-4 py-2 text-sm font-medium text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
              onClick={handleRegenerate}
            >
              重新生成每日一题
            </button>
          </div>
        </div>
      ) : (
        <div className="text-center py-8 text-sm text-text-secondary">
          <p>今日题目尚未生成</p>
          <button
            className="mt-3 px-4 py-2 text-sm font-medium text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
            onClick={handleRegenerate}
          >
            生成今日题
          </button>
        </div>
      )}
    </div>
  );
}
