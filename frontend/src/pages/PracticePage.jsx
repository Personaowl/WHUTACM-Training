import { useEffect, useState, useCallback } from "react";
import {
  drawPracticeProblem,
  getPracticeHistory,
  deletePracticeDraw,
  getPracticeTags,
} from "../api/practice";
import ProblemCard from "../components/ui/ProblemCard";
import AddToProblemListButton from "../components/ui/AddToProblemListButton";
import EmptyState from "../components/ui/EmptyState";
import { ListSkeleton } from "../components/ui/Skeleton";
import { Crosshair, Shuffle } from "lucide-react";
import PageHeading from "../components/ui/PageHeading";

const RATING_OPTIONS = Array.from({ length: 28 }, (_, index) => 800 + index * 100);

export default function PracticePage() {
  const [minRating, setMinRating] = useState(1200);
  const [maxRating, setMaxRating] = useState(1600);
  const [drawResult, setDrawResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [availableTags, setAvailableTags] = useState([]);
  const [selectedTags, setSelectedTags] = useState([]);
  const [tagQuery, setTagQuery] = useState("");
  const [tagsLoading, setTagsLoading] = useState(true);

  // Practice history
  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [deletingId, setDeletingId] = useState(null);

  const loadHistory = useCallback(async () => {
    try {
      const resp = await getPracticeHistory(50);
      if (resp.code === 200) {
        setHistory(resp.data || []);
      }
    } catch {
      // ignore
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  useEffect(() => {
    let cancelled = false;
    async function loadTags() {
      try {
        const resp = await getPracticeTags();
        if (!cancelled && resp.code === 200) {
          setAvailableTags(resp.data || []);
        }
      } catch {
        // The draw form remains usable with rating-only filtering.
      } finally {
        if (!cancelled) setTagsLoading(false);
      }
    }
    loadTags();
    return () => { cancelled = true; };
  }, []);

  function toggleTag(tag) {
    setSelectedTags((current) => (
      current.includes(tag)
        ? current.filter((item) => item !== tag)
        : current.length < 5 ? [...current, tag] : current
    ));
  }

  async function handleDraw() {
    setLoading(true);
    setMessage("");
    setDrawResult(null);
    try {
      const resp = await drawPracticeProblem(
        minRating || null,
        maxRating || null,
        selectedTags
      );
      if (resp.code === 200) {
        setDrawResult(resp.data);
        loadHistory(); // refresh history after draw
      } else {
        setMessage(resp.message || "抽题失败");
      }
    } catch {
      setMessage("抽题请求失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(drawId) {
    setDeletingId(drawId);
    try {
      const resp = await deletePracticeDraw(drawId);
      if (resp.code === 200) {
        setHistory((prev) => prev.filter((item) => item.drawId !== drawId));
      }
    } catch {
      setMessage("删除失败");
    } finally {
      setDeletingId(null);
    }
  }

  const normalizedTagQuery = tagQuery.trim().toLowerCase();
  const visibleTags = availableTags.filter((tag) => (
    !normalizedTagQuery || tag.includes(normalizedTagQuery)
  ));

  return (
    <div className="practice-page stack-y-5">
      <PageHeading icon={Crosshair} title="自主练习" tone="violet" />

      {/* Draw panel */}
      <div className="practice-draw-panel bg-white border border-border rounded-ui p-4">
        <div className="flex items-end gap-3 flex-wrap">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-text-secondary">最小难度</span>
            <select
              className="w-28 px-3 py-2 text-sm bg-white border border-border rounded-ui outline-hidden focus:border-text-primary"
              value={minRating}
              onChange={(e) => {
                const rating = Number(e.target.value);
                setMinRating(rating);
                setMaxRating((current) => Math.max(current, rating));
              }}
            >
              {RATING_OPTIONS.map((rating) => (
                <option key={rating} value={rating}>{rating}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-text-secondary">最大难度</span>
            <select
              className="w-28 px-3 py-2 text-sm bg-white border border-border rounded-ui outline-hidden focus:border-text-primary"
              value={maxRating}
              onChange={(e) => {
                const rating = Number(e.target.value);
                setMaxRating(rating);
                setMinRating((current) => Math.min(current, rating));
              }}
            >
              {RATING_OPTIONS.map((rating) => (
                <option key={rating} value={rating}>{rating}</option>
              ))}
            </select>
          </label>
          <button
            className="button-primary inline-flex items-center gap-2"
            onClick={handleDraw}
            disabled={loading}
          >
            <Shuffle size={18} aria-hidden="true" />{loading ? "抽取中..." : "抽题"}
          </button>
        </div>

        <div className="mt-4 pt-4 border-t border-border">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div>
              <p className="text-sm font-semibold text-text-primary m-0">算法标签</p>
              <p className="text-xs text-text-secondary mt-1 mb-0">
                最多 5 个，题目需同时包含所选标签
              </p>
            </div>
            {selectedTags.length > 0 && (
              <button
                type="button"
                className="text-xs text-text-secondary bg-transparent border-0 cursor-pointer hover:text-text-primary"
                onClick={() => setSelectedTags([])}
              >
                清空标签 ({selectedTags.length})
              </button>
            )}
          </div>

          <input
            className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary box-border"
            value={tagQuery}
            onChange={(event) => setTagQuery(event.target.value)}
            placeholder="搜索标签，例如 dp、binary search、greedy"
            aria-label="搜索算法标签"
          />

          <div className="flex flex-wrap gap-2 mt-3 max-h-36 overflow-y-auto pr-1">
            {tagsLoading ? (
              <span className="text-xs text-text-secondary">正在读取题库标签...</span>
            ) : visibleTags.length > 0 ? (
              visibleTags.map((tag) => {
                const selected = selectedTags.includes(tag);
                return (
                  <button
                    key={tag}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => toggleTag(tag)}
                    className={`px-2.5 py-1.5 text-xs rounded-full cursor-pointer transition-colors ${
                      selected
                        ? "bg-text-primary text-white border border-text-primary"
                        : "bg-white text-text-secondary border border-border hover:border-text-primary hover:text-text-primary"
                    }`}
                  >
                    {tag}
                  </button>
                );
              })
            ) : (
              <span className="text-xs text-text-secondary">
                {availableTags.length === 0 ? "题库初始化完成后会显示可选标签" : "没有匹配的标签"}
              </span>
            )}
          </div>
        </div>
      </div>

      {message && (
        <p
          className={`text-sm rounded-ui px-3 py-2 m-0 ${
            message.includes("通过") || message.includes("成功")
              ? "bg-[#f0fff0] text-success"
              : "text-text-secondary bg-bg-secondary"
          }`}
        >
          {message}
        </p>
      )}

      {/* Draw result */}
      {drawResult?.problem && (
        <ProblemCard
          problem={drawResult.problem}
          actions={<AddToProblemListButton problem={drawResult.problem} />}
        />
      )}

      {/* Practice history */}
      <section>
        <h2 className="text-base font-semibold text-text-primary m-0 mb-2">抽题历史</h2>
        {historyLoading ? (
          <ListSkeleton rows={3} />
        ) : history.length === 0 ? (
          <EmptyState title="暂无抽题记录" />
        ) : (
          <div className="stack-y-2">
            {history.map((item) => (
              <div
                key={item.drawId}
                className="bg-white border border-border rounded-ui p-3 flex items-center justify-between gap-3"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <a
                      href={item.sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-sm font-medium text-text-primary hover:underline truncate"
                    >
                      {item.name}
                    </a>
                    <span className="text-xs text-text-secondary">
                      ({item.rating ?? "-"})
                    </span>
                  </div>
                  <p className="text-xs text-text-secondary mt-0.5 m-0">
                    {item.drawDate}{item.drawnAt ? ` · ${item.drawnAt.slice(11, 19)}` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <AddToProblemListButton problem={item} compact />
                  <button
                    className="px-2 py-1 text-xs text-error border border-border rounded-sm hover:bg-[#fff0f0] cursor-pointer disabled:opacity-40 bg-white"
                    onClick={() => handleDelete(item.drawId)}
                    disabled={deletingId === item.drawId}
                  >
                    {deletingId === item.drawId ? "..." : "删除"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
