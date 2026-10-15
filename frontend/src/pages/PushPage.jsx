import { useEffect, useState, useCallback } from "react";
import {
  getTodayPush,
  getMyPushes,
  getPushHistory,
  submitPush,
  submitPushSolution,
  getPushSubmissions,
} from "../api/push";
import EmptyState from "../components/ui/EmptyState";
import { CardSkeleton } from "../components/ui/Skeleton";
import * as Accordion from "@radix-ui/react-accordion";

function getTodayStr() {
  return new Date().toISOString().slice(0, 10);
}

export default function PushPage() {
  const [todayPush, setTodayPush] = useState(null);
  const [myItems, setMyItems] = useState([]);
  const [historyItems, setHistoryItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  // Submit push form
  const [title, setTitle] = useState("");
  const [link, setLink] = useState("");
  const [description, setDescription] = useState("");
  const [submitMessage, setSubmitMessage] = useState("");
  const [submittingPush, setSubmittingPush] = useState(false);

  // Submit solution modal
  const [solvePushId, setSolvePushId] = useState(null);
  const [solutionLink, setSolutionLink] = useState("");
  const [solutionDesc, setSolutionDesc] = useState("");

  // Submissions view
  const [viewSubmissions, setViewSubmissions] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [todayResp, mineResp, historyResp] = await Promise.all([
        getTodayPush(),
        getMyPushes(),
        getPushHistory(),
      ]);
      if (todayResp.code === 200) {
        setTodayPush(todayResp.data);
      }
      if (mineResp.code === 200) {
        setMyItems(mineResp.data || []);
      }
      if (historyResp.code === 200) {
        setHistoryItems(historyResp.data || []);
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  async function handleSubmitPush() {
    if (!title.trim() || !link.trim()) {
      setSubmitMessage("标题和链接不能为空");
      return;
    }
    setSubmitMessage("");
    setSubmittingPush(true);
    try {
      const resp = await submitPush(title.trim(), link.trim(), description.trim() || null);
      if (resp.code === 200) {
        setTitle("");
        setLink("");
        setDescription("");
        setSubmitMessage("推题提交成功，正在等待管理员审核");
        loadData();
      } else {
        setSubmitMessage(resp.message || "提交失败");
      }
    } catch (error) {
      setSubmitMessage(error?.response?.data?.message || "提交请求失败，请稍后重试");
    } finally {
      setSubmittingPush(false);
    }
  }

  async function handleSubmitSolution() {
    if (!solutionLink.trim()) {
      setMessage("解答链接不能为空");
      return;
    }
    try {
      const resp = await submitPushSolution(solvePushId, solutionLink.trim(), solutionDesc.trim() || null);
      if (resp.code === 200) {
        setSolvePushId(null);
        setSolutionLink("");
        setSolutionDesc("");
        setMessage("解答提交成功");
      } else {
        setMessage(resp.message || "提交失败");
      }
    } catch {
      setMessage("提交请求失败");
    }
  }

  async function loadSubmissions(pushId) {
    try {
      const resp = await getPushSubmissions(pushId);
      if (resp.code === 200) {
        setViewSubmissions({ pushId, items: resp.data || [] });
      }
    } catch {
      // ignore
    }
  }

  return (
    <div className="stack-y-5">
      <h1 className="text-lg font-semibold text-text-primary m-0">推题系统</h1>

      {message && (
        <p className="text-sm bg-bg-secondary text-text-secondary rounded-ui px-3 py-2 m-0">{message}</p>
      )}

      {/* 今日推题 */}
      <section>
        <h2 className="text-base font-semibold text-text-primary m-0 mb-3">今日推题</h2>
        {loading ? (
          <CardSkeleton />
        ) : todayPush ? (
          <TodayPushCard
            item={todayPush}
            onSolve={() => setSolvePushId(todayPush.id)}
            onViewSubmissions={() => loadSubmissions(todayPush.id)}
          />
        ) : (
          <EmptyState title="今日暂无推题" description="管理员审核通过后，系统将在每日凌晨自动推送" />
        )}
      </section>

      {/* 历史推题 */}
      <section>
        <h2 className="text-base font-semibold text-text-primary m-0 mb-3">
          历史推题 ({historyItems.length})
        </h2>
        {loading ? (
          <CardSkeleton />
        ) : historyItems.length === 0 ? (
          <EmptyState title="暂无历史推题" description="系统推送的题目将在此处显示" />
        ) : (
          <div className="stack-y-2">
            {historyItems
              .filter((item) => String(item.id) !== String(todayPush?.id))
              .map((item) => (
                <HistoryPushCard
                  key={item.id}
                  item={item}
                  onSolve={() => setSolvePushId(item.id)}
                  onViewSubmissions={() => loadSubmissions(item.id)}
                />
              ))}
          </div>
        )}
      </section>

      {/* 我的推题 */}
      <Accordion.Root type="multiple" className="stack-y-2">
        <Accordion.Item value="mine" className="bg-white border border-border rounded-ui overflow-hidden">
          <Accordion.Trigger className="flex items-center justify-between w-full px-4 py-3 text-sm font-medium text-text-primary hover:bg-bg-secondary border-0 bg-transparent cursor-pointer">
            <span>我的推题 ({myItems.length})</span>
            <Chevron />
          </Accordion.Trigger>
          <Accordion.Content className="border-t border-border px-4 py-3">
            {myItems.length === 0 ? (
              <EmptyState title="暂无我的推题" description="提交推题后，可在此查看审核状态" />
            ) : (
              <div className="stack-y-2">
                {myItems.map((item) => (
                  <div key={item.id} className="flex items-start justify-between gap-3 p-3 border border-border rounded-ui">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary m-0 truncate">{item.title}</p>
                      {item.link && (
                        <a href={item.link} target="_blank" rel="noreferrer" className="text-xs text-text-secondary underline break-all">
                          {item.link}
                        </a>
                      )}
                      {item.description && (
                        <p className="text-xs text-text-secondary mt-0.5 m-0 line-clamp-1">{item.description}</p>
                      )}
                      <span
                        className={
                          "text-xs px-1.5 py-0.5 rounded-sm mt-1 inline-block " +
                          (item.status === "APPROVED"
                            ? "bg-[#f0fff0] text-success"
                            : item.status === "PUBLISHED"
                            ? "bg-[#f0f0ff] text-[#5865f2]"
                            : item.status === "PENDING"
                            ? "bg-[#fff8f0] text-warning"
                            : "bg-[#fff0f0] text-error")
                        }
                      >
                        {item.status === "APPROVED"
                          ? "已通过"
                          : item.status === "PUBLISHED"
                          ? "已推送"
                          : item.status === "PENDING"
                          ? "审核中"
                          : "已拒绝"}
                      </span>
                    </div>
                    <div className="flex gap-1 shrink-0">
                      {(item.status === "APPROVED" || item.status === "PUBLISHED") && (
                        <button
                          className="px-2 py-1 text-xs font-medium text-white bg-success hover:bg-[#268845] rounded-sm border-0 cursor-pointer"
                          onClick={() => setSolvePushId(item.id)}
                        >
                          提交解答
                        </button>
                      )}
                      <button
                        className="px-2 py-1 text-xs text-text-secondary border border-border rounded-sm bg-white hover:bg-bg-secondary cursor-pointer"
                        onClick={() => loadSubmissions(item.id)}
                      >
                        查看提交
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Accordion.Content>
        </Accordion.Item>

        {/* 提交推题 */}
        <Accordion.Item value="submit" className="bg-white border border-border rounded-ui overflow-hidden">
          <Accordion.Trigger className="flex items-center justify-between w-full px-4 py-3 text-sm font-medium text-text-primary hover:bg-bg-secondary border-0 bg-transparent cursor-pointer">
            <span>提交推题</span>
            <Chevron />
          </Accordion.Trigger>
          <Accordion.Content className="border-t border-border px-4 py-3">
            <div className="stack-y-3">
              <input
                className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="题目标题 *"
                maxLength={255}
              />
              <input
                className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary"
                value={link}
                onChange={(e) => setLink(e.target.value)}
                placeholder="题目链接 *（可直接填写 codeforces.com/...）"
                maxLength={1000}
              />
              <textarea
                className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary resize-y"
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="题目描述（可选）"
                maxLength={2000}
              />
              {submitMessage && (
                <p className="m-0 rounded-ui bg-bg-secondary px-3 py-2 text-sm text-text-secondary">
                  {submitMessage}
                </p>
              )}
              <button
                className="px-4 py-2 text-sm font-medium text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
                onClick={handleSubmitPush}
                disabled={submittingPush}
              >
                {submittingPush ? "提交中..." : "提交推题"}
              </button>
            </div>
          </Accordion.Content>
        </Accordion.Item>
      </Accordion.Root>

      {/* Solve modal */}
      {solvePushId && (
        <div className="fixed inset-0 bg-black/30 z-40 flex items-center justify-center" onClick={() => setSolvePushId(null)}>
          <div className="bg-white rounded-ui border border-border p-6 w-[400px] max-w-[90vw] shadow-lg z-50" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-semibold text-text-primary m-0 mb-4">提交解答</h3>
            <input
              className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary mb-2"
              value={solutionLink}
              onChange={(e) => setSolutionLink(e.target.value)}
              placeholder="解答链接 *"
            />
            <textarea
              className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary resize-y mb-3"
              rows={2}
              value={solutionDesc}
              onChange={(e) => setSolutionDesc(e.target.value)}
              placeholder="解答描述（可选）"
            />
            <div className="flex justify-end gap-2">
              <button
                className="px-3 py-1.5 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer"
                onClick={() => setSolvePushId(null)}
              >
                取消
              </button>
              <button
                className="px-3 py-1.5 text-sm text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
                onClick={handleSubmitSolution}
              >
                提交
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submissions view modal */}
      {viewSubmissions && (
        <div className="fixed inset-0 bg-black/30 z-40 flex items-center justify-center" onClick={() => setViewSubmissions(null)}>
          <div className="bg-white rounded-ui border border-border p-6 w-[500px] max-w-[90vw] max-h-[80vh] overflow-y-auto shadow-lg z-50" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-semibold text-text-primary m-0 mb-4">解答提交记录</h3>
            {viewSubmissions.items.length === 0 ? (
              <EmptyState title="暂无提交" />
            ) : (
              <div className="stack-y-2">
                {viewSubmissions.items.map((sub) => (
                  <div key={sub.id} className="p-3 border border-border rounded-ui">
                    <a href={sub.submissionLink} target="_blank" rel="noreferrer" className="text-sm text-text-primary underline break-all">
                      {sub.submissionLink}
                    </a>
                    {sub.resultDescription && (
                      <p className="text-xs text-text-secondary mt-1 m-0">{sub.resultDescription}</p>
                    )}
                    {sub.createdAt && (
                      <p className="text-xs text-text-secondary mt-0.5 m-0">{sub.createdAt}</p>
                    )}
                  </div>
                ))}
              </div>
            )}
            <div className="flex justify-end mt-4">
              <button
                className="px-3 py-1.5 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer"
                onClick={() => setViewSubmissions(null)}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** 今日推题卡片——展示标题、链接、描述与推题用户名 */
function TodayPushCard({ item, onSolve, onViewSubmissions }) {
  return (
    <div className="bg-white border border-border rounded-ui p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-text-primary m-0 truncate">{item.title}</h3>
          {item.submitterUsername && (
            <p className="text-xs text-text-secondary mt-1 m-0">
              推题人：{item.submitterUsername}
            </p>
          )}
          {item.description && (
            <p className="text-sm text-text-primary mt-2 m-0">{item.description}</p>
          )}
          {item.link && (
            <a
              href={item.link}
              target="_blank"
              rel="noreferrer"
              className="inline-block mt-2 text-xs text-text-secondary hover:text-text-primary underline break-all"
            >
              {item.link}
            </a>
          )}
        </div>
        <div className="flex gap-1.5 shrink-0">
          <button
            className="px-2.5 py-1 text-xs font-medium text-white bg-success hover:bg-[#268845] rounded-sm border-0 cursor-pointer"
            onClick={onSolve}
          >
            提交解答
          </button>
          <button
            className="px-2.5 py-1 text-xs text-text-secondary border border-border rounded-sm bg-white hover:bg-bg-secondary cursor-pointer"
            onClick={onViewSubmissions}
          >
            查看提交
          </button>
        </div>
      </div>
    </div>
  );
}

/** 历史推题卡片——展示标题、链接、描述、推题用户名与推送日期 */
function HistoryPushCard({ item, onSolve, onViewSubmissions }) {
  return (
    <div className="bg-white border border-border rounded-ui p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-sm font-semibold text-text-primary m-0 truncate">{item.title}</h3>
            {item.pushDate && (
              <span className="text-xs text-text-secondary shrink-0">{item.pushDate}</span>
            )}
          </div>
          {item.submitterUsername && (
            <p className="text-xs text-text-secondary mt-1 m-0">
              推题人：{item.submitterUsername}
            </p>
          )}
          {item.description && (
            <p className="text-xs text-text-secondary mt-0.5 m-0 line-clamp-1">{item.description}</p>
          )}
          {item.link && (
            <a
              href={item.link}
              target="_blank"
              rel="noreferrer"
              className="inline-block mt-1 text-xs text-text-secondary hover:text-text-primary underline break-all"
            >
              {item.link}
            </a>
          )}
        </div>
        <div className="flex gap-1 shrink-0">
          <button
            className="px-2 py-1 text-xs font-medium text-white bg-success hover:bg-[#268845] rounded-sm border-0 cursor-pointer"
            onClick={onSolve}
          >
            提交解答
          </button>
          <button
            className="px-2 py-1 text-xs text-text-secondary border border-border rounded-sm bg-white hover:bg-bg-secondary cursor-pointer"
            onClick={onViewSubmissions}
          >
            查看提交
          </button>
        </div>
      </div>
    </div>
  );
}

function Chevron() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  );
}
