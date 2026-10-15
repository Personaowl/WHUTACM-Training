import { useEffect, useState } from "react";
import { listPush, approvePushItem, rejectPushItem, promotePushItem, deletePushItem, getPushPool, getPushHistory } from "../../api/push";
import EmptyState from "../../components/ui/EmptyState";
import { ListSkeleton } from "../../components/ui/Skeleton";

export default function AdminPushPage() {
  const [items, setItems] = useState([]);
  const [poolItems, setPoolItems] = useState([]);
  const [historyItems, setHistoryItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [deletingId, setDeletingId] = useState(null);

  async function load() {
    try {
      const [listResp, poolResp, historyResp] = await Promise.all([
        listPush(),
        getPushPool(),
        getPushHistory(),
      ]);
      if (listResp.code === 200) setItems(listResp.data || []);
      if (poolResp.code === 200) setPoolItems(poolResp.data || []);
      if (historyResp.code === 200) setHistoryItems(historyResp.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  // 构建 pushId → pushDate 映射，供已推送区块展示推送日期
  const pushDateMap = {};
  for (const h of historyItems) {
    if (h.id != null && h.pushDate) {
      pushDateMap[h.id] = h.pushDate;
    }
  }

  useEffect(() => { load(); }, []);

  async function handleApprove(id) {
    try {
      const resp = await approvePushItem(id);
      if (resp.code === 200) load();
      else setMessage(resp.message || "操作失败");
    } catch { setMessage("请求失败"); }
  }

  async function handleReject(id) {
    try {
      const resp = await rejectPushItem(id);
      if (resp.code === 200) load();
      else setMessage(resp.message || "操作失败");
    } catch { setMessage("请求失败"); }
  }

  async function handlePromote(id) {
    try {
      const resp = await promotePushItem(id);
      if (resp.code === 200) load();
      else setMessage(resp.message || "操作失败");
    } catch { setMessage("请求失败"); }
  }

  async function handleDelete(id) {
    if (!window.confirm("确定要删除该推题吗？")) return;
    setDeletingId(id);
    try {
      const resp = await deletePushItem(id);
      if (resp.code === 200) {
        load();
        setMessage("删除成功");
      } else {
        setMessage(resp.message || "删除失败");
      }
    } catch {
      setMessage("请求失败");
    } finally {
      setDeletingId(null);
    }
  }

  const pending = items.filter((i) => i.status === "PENDING");
  const approved = items.filter((i) => i.status === "APPROVED");
  const rejected = items.filter((i) => i.status === "REJECTED");
  const published = items.filter((i) => i.status === "PUBLISHED");

  return (
    <div className="stack-y-5">
      <h1 className="text-lg font-semibold text-text-primary m-0">推题审核</h1>

      {message && (
        <p className="text-sm bg-bg-secondary text-text-secondary rounded-ui px-3 py-2 m-0">{message}</p>
      )}

      {loading ? (
        <ListSkeleton rows={5} />
      ) : (
        <>
          {/* Pending */}
          <section>
            <h2 className="text-base font-semibold text-text-primary m-0 mb-2">
              待审核 ({pending.length})
            </h2>
            {pending.length === 0 ? (
              <EmptyState title="暂无待审核推题" />
            ) : (
              <div className="stack-y-2">
                {pending.map((item) => (
                  <div key={item.id} className="bg-white border border-border rounded-ui p-3 flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary m-0">{item.title}</p>
                      {item.submitterUsername && (
                        <span className="text-xs text-text-secondary">推题人：{item.submitterUsername}</span>
                      )}
                      {item.link && (
                        <a href={item.link} target="_blank" rel="noreferrer" className="text-xs text-text-secondary underline ml-2">
                          {item.link}
                        </a>
                      )}
                      {item.description && <p className="text-xs text-text-secondary mt-0.5 m-0">{item.description}</p>}
                    </div>
                    <div className="flex gap-1 shrink-0">
                      <button className="px-2.5 py-1 text-xs font-medium text-white bg-success hover:bg-[#268845] rounded-sm border-0 cursor-pointer"
                        onClick={() => handleApprove(item.id)}>通过</button>
                      <button className="px-2.5 py-1 text-xs font-medium text-white bg-error hover:bg-[#b01e28] rounded-sm border-0 cursor-pointer"
                        onClick={() => handleReject(item.id)}>拒绝</button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Pool - approved but unpublished */}
          <section>
            <h2 className="text-base font-semibold text-text-primary m-0 mb-2">
              推题池 · 待推送 ({poolItems.length})
            </h2>
            {poolItems.length === 0 ? (
              <EmptyState title="推题池为空" description="审核通过推题后，未被推送的题目会出现在这里" />
            ) : (
              <div className="stack-y-2">
                {poolItems.map((item) => (
                  <div key={item.id} className="bg-white border border-border rounded-ui p-3 flex items-center justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary m-0">{item.title}</p>
                      {item.submitterUsername && (
                        <span className="text-xs text-text-secondary">推题人：{item.submitterUsername}</span>
                      )}
                    </div>
                    <div className="flex gap-1 shrink-0">
                      <button className="px-2.5 py-1 text-xs border border-border rounded-sm bg-white text-text-secondary hover:bg-bg-secondary cursor-pointer"
                        onClick={() => handlePromote(item.id)}>提升</button>
                      <button className="px-2.5 py-1 text-xs font-medium text-white bg-error hover:bg-[#b01e28] rounded-sm border-0 cursor-pointer disabled:opacity-40"
                        onClick={() => handleDelete(item.id)}
                        disabled={deletingId === item.id}>
                        {deletingId === item.id ? "..." : "删除"}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Published */}
          <section>
            <h2 className="text-base font-semibold text-text-primary m-0 mb-2">
              已推送 ({published.length})
            </h2>
            {published.length === 0 ? (
              <EmptyState title="暂无已推送推题" description="系统每日凌晨自动推送，或用户访问时触发懒发布" />
            ) : (
              <div className="stack-y-2">
                {published.map((item) => (
                  <div key={item.id} className="bg-white border border-border rounded-ui p-3 flex items-center justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary m-0">{item.title}</p>
                      {item.submitterUsername && (
                        <span className="text-xs text-text-secondary">推题人：{item.submitterUsername}</span>
                      )}
                      {pushDateMap[item.id] && (
                        <span className="text-xs text-text-secondary ml-2">推送日期：{pushDateMap[item.id]}</span>
                      )}
                      {item.link && (
                        <a href={item.link} target="_blank" rel="noreferrer" className="text-xs text-text-secondary underline ml-2">
                          {item.link}
                        </a>
                      )}
                    </div>
                    <span className="text-xs px-1.5 py-0.5 rounded-sm bg-[#f0f0ff] text-[#5865f2] shrink-0">已推送</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Approved */}
          <section>
            <h2 className="text-base font-semibold text-text-primary m-0 mb-2">
              已通过 ({approved.length})
            </h2>
            {approved.length === 0 ? (
              <EmptyState title="暂无已通过推题" />
            ) : (
              <div className="stack-y-2">
                {approved.map((item) => (
                  <div key={item.id} className="bg-white border border-border rounded-ui p-3 flex items-center justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary m-0">{item.title}</p>
                      {item.submitterUsername && (
                        <span className="text-xs text-text-secondary">推题人：{item.submitterUsername}</span>
                      )}
                    </div>
                    <button className="px-2.5 py-1 text-xs border border-border rounded-sm bg-white text-text-secondary hover:bg-bg-secondary cursor-pointer"
                      onClick={() => handlePromote(item.id)}>提升</button>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
