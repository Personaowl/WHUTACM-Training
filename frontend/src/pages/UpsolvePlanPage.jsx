import { useEffect, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Check, CheckCheck, ChevronLeft, ChevronRight, ExternalLink, ListTodo, LoaderCircle, NotebookPen, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-react";
import { completeUpsolvePlan, deleteUpsolvePlan, listUpsolvePlans, saveUpsolvePlan } from "../api/upsolve";
import { problemHost, requireUpsolveData, upsolveDate } from "../utils/upsolve";
import AddToProblemListButton from "../components/ui/AddToProblemListButton";
import { useAuth } from "../context/AuthContext";
import "./UpsolvePlanPage.css";
import { PageIcon } from "../components/ui/PageHeading";

const EMPTY_FORM = { link: "", title: "", note: "" };

export default function UpsolvePlanPage() {
  const { user } = useAuth();
  const [completed, setCompleted] = useState(false);
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loadFailed, setLoadFailed] = useState(false);
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const [quickLink, setQuickLink] = useState("");
  const [editor, setEditor] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");
    setLoadFailed(false);
    listUpsolvePlans(completed, page, controller.signal)
      .then(response => {
        if (!active) return;
        const next = requireUpsolveData(response);
        if (page > 1 && next.items.length === 0) { setPage(p => p - 1); return; }
        setData(next);
      })
      .catch(e => { if (active) { setError(e.message || "补题计划加载失败"); setLoadFailed(true); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [completed, page, reload, user?.id]);

  async function mutate(action, message, after) {
    if (busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      requireUpsolveData(await action());
      after?.(); setNotice(message); setReload(v => v + 1);
    } catch (e) { setError(e.message || "操作失败，请重试"); }
    finally { setBusy(false); }
  }
  function openEditor(item = null) {
    setEditor({ id: item?.id ?? null });
    setForm(item ? { link: item.link, title: item.title, note: item.note || "" } : { ...EMPTY_FORM, link: quickLink });
    setFormError("");
  }
  async function submitEditor(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setFormError("");
    try {
      requireUpsolveData(await saveUpsolvePlan(editor.id, form));
      if (editor.id == null) setCompleted(false);
      setNotice(editor.id == null ? "已加入补题计划" : "已保存修改");
      setError(""); setEditor(null); setQuickLink(""); setPage(1); setReload(v => v + 1);
    } catch (e) { setFormError(e.message || "保存失败，请重试"); }
    finally { setBusy(false); }
  }
  function selectStatus(next) { setCompleted(next); setPage(1); setNotice(""); }

  return (
    <div className="upsolve-page">
      <header className="upsolve-header">
        <div className="upsolve-heading"><PageIcon icon={NotebookPen} tone="violet" /><div><h1>补题计划</h1></div></div>
        <button type="button" className="button-primary" onClick={() => openEditor()} disabled={busy}><Plus size={17} />添加题目</button>
      </header>

      <section className="upsolve-panel" aria-label="我的补题计划">
        <form className="upsolve-quick" onSubmit={event => {
          event.preventDefault();
          mutate(() => saveUpsolvePlan(null, { link: quickLink }), "已加入补题计划", () => {
            setQuickLink(""); setCompleted(false); setPage(1);
          });
        }}>
          <label className="sr-only" htmlFor="upsolve-quick-link">快速添加题目链接</label>
          <input id="upsolve-quick-link" className="form-input" type="url" required maxLength={1000} placeholder="粘贴题目链接，加入待补清单" value={quickLink} onChange={e => setQuickLink(e.target.value)} disabled={busy} />
          <button type="submit" className="button-primary" disabled={busy || !quickLink.trim()}><Plus size={16} />添加</button>
        </form>
        <div className="upsolve-toolbar">
          <div className="upsolve-tabs" role="group" aria-label="完成状态">
            <button type="button" aria-pressed={!completed} className={!completed ? "is-active" : ""} onClick={() => selectStatus(false)} disabled={busy}><ListTodo size={16} />待完成 <span>{data?.pendingCount ?? "—"}</span></button>
            <button type="button" aria-pressed={completed} className={completed ? "is-active" : ""} onClick={() => selectStatus(true)} disabled={busy}><CheckCheck size={16} />已完成 <span>{data?.completedCount ?? "—"}</span></button>
          </div>
          <span className="upsolve-private">{data ? `${data.pendingCount} 题待补` : ""}</span>
        </div>
        {notice && <div className="upsolve-notice" role="status"><Check size={16} />{notice}<button type="button" aria-label="关闭提示" onClick={() => setNotice("")}><X size={14} /></button></div>}
        {error && <div className="upsolve-error" role="alert">{error}<button type="button" onClick={() => setReload(v => v + 1)}>重新加载</button></div>}
        <div aria-busy={loading || busy}>
          {loading ? <div className="upsolve-empty" role="status"><LoaderCircle className="animate-spin" size={24} /><p>正在读取补题计划</p></div>
            : loadFailed ? null : !data?.items.length ? <div className="upsolve-empty"><span className="upsolve-empty-icon">{completed ? <CheckCheck size={30} /> : <ListTodo size={30} />}</span><h2>{completed ? "还没有完成记录" : "待补清单是空的"}</h2><p>{completed ? "完成一题后，记录会保留在这里。" : "添加一道想补的题目，从这一题开始。"}</p>{!completed && <button type="button" className="button-ghost" onClick={() => openEditor()}><Plus size={16} />添加第一道题</button>}</div>
              : <ul className="upsolve-list">{data.items.map(item => <li key={item.id} className={`upsolve-row ${item.completed ? "is-completed" : ""}`}>
                <input type="checkbox" className="upsolve-checkbox" checked={item.completed} disabled={busy}
                  aria-label={`${item.completed ? "撤销完成" : "完成"}：${item.title}`}
                  onChange={e => { const next=e.target.checked; mutate(() => completeUpsolvePlan(item.id,next),next ? "已移到已完成，可随时加入题单" : "已恢复到待完成"); }} />
                <div className="upsolve-problem">
                  <a href={item.link} target="_blank" rel="noopener noreferrer" className="upsolve-title">{item.title}<ExternalLink size={14} /></a>
                  <div className="upsolve-meta"><span>{problemHost(item.link)}</span><span>加入于 {upsolveDate(item.createdAt)}</span>{item.completedAt && <span>完成于 {upsolveDate(item.completedAt)}</span>}</div>
                  {item.note && <p className="upsolve-note">{item.note}</p>}
                  <a href={item.link} target="_blank" rel="noopener noreferrer" className="upsolve-url" title={item.link}>{item.link}</a>
                </div>
                <div className="upsolve-actions">
                  <AddToProblemListButton problem={item} initialNote={item.note || ""} />
                  {item.completed && <button type="button" className="upsolve-icon-button" title="撤销完成" aria-label={`恢复：${item.title}`} disabled={busy} onClick={() => mutate(() => completeUpsolvePlan(item.id,false),"已恢复到待完成")}><RotateCcw size={16} /></button>}
                  <button type="button" className="upsolve-icon-button" aria-label={`编辑：${item.title}`} title="编辑" disabled={busy} onClick={() => openEditor(item)}><Pencil size={16} /></button>
                  <button type="button" className="upsolve-icon-button is-danger" aria-label={`删除：${item.title}`} title="删除" disabled={busy} onClick={() => {
                    if (window.confirm(`确定删除“${item.title}”的补题记录吗？此操作不会删除题单中的题目。`)) mutate(() => deleteUpsolvePlan(item.id),"已删除补题记录");
                  }}><Trash2 size={16} /></button>
                </div>
              </li>)}</ul>}
        </div>
        {!loading && !error && data?.total > 0 && <footer className="upsolve-pagination"><span>共 {data.total} 道题 · 第 {page} / {Math.max(1,Math.ceil(data.total / 20))} 页</span><div><button type="button" disabled={page===1 || busy} aria-label="上一页" onClick={() => setPage(v=>v-1)}><ChevronLeft size={18} /></button><button type="button" disabled={page*20>=data.total || busy} aria-label="下一页" onClick={() => setPage(v=>v+1)}><ChevronRight size={18} /></button></div></footer>}
      </section>

      <Dialog.Root open={editor !== null} onOpenChange={open => { if (!open && !busy) setEditor(null); }}>
        <Dialog.Portal><Dialog.Overlay className="upsolve-overlay" /><Dialog.Content className="upsolve-dialog">
          <div className="upsolve-dialog-heading"><Dialog.Title>{editor?.id == null ? "添加补题" : "编辑补题"}</Dialog.Title><Dialog.Close asChild><button type="button" className="upsolve-icon-button" aria-label="关闭" disabled={busy}><X size={20} /></button></Dialog.Close></div>
          <Dialog.Description className="sr-only">保存题目链接，可选填标题和备注。完成状态由你手动标记。</Dialog.Description>
          <form onSubmit={submitEditor} className="upsolve-editor">
            <label>题目链接<input className="form-input" type="url" required maxLength={1000} value={form.link} onChange={e=>setForm({...form,link:e.target.value})} placeholder="https://…" disabled={busy} /></label>
            <label>题目名称 <span>（可选）</span><input className="form-input" maxLength={255} value={form.title} onChange={e=>setForm({...form,title:e.target.value})} placeholder="例如：ABC 380 F · Exchange Game" disabled={busy} /></label>
            <label>备注 <span>（可选）</span><textarea className="form-input" rows={3} maxLength={1000} value={form.note} onChange={e=>setForm({...form,note:e.target.value})} placeholder="记录没做出的原因或想再尝试的思路" disabled={busy} /></label>
            {formError && <p className="text-error" role="alert">{formError}</p>}
            <div className="upsolve-dialog-actions"><Dialog.Close asChild><button type="button" className="button-ghost" disabled={busy}>取消</button></Dialog.Close><button type="submit" className="button-primary" disabled={busy}>{busy ? "保存中…" : "保存"}</button></div>
          </form>
        </Dialog.Content></Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
