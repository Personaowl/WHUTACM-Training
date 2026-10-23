import { useEffect, useMemo, useRef, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import {
  ExternalLink,
  FileText,
  FolderOpen,
  FolderPlus,
  Globe2,
  LockKeyhole,
  Pencil,
  Plus,
  Trash2,
  X,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import {
  addProblemListItem,
  createProblemList,
  deleteProblemList,
  deleteProblemListItem,
  getProblemList,
  listProblemLists,
  updateProblemList,
  updateProblemListItem,
} from "../api/problemList";
import { canAddProblem, canEditProblem, visibleProblemLists } from "../utils/problemListPermissions";
import EmptyState from "../components/ui/EmptyState";
import AddToProblemListButton from "../components/ui/AddToProblemListButton";

const EMPTY_LIST_FORM = { name: "", description: "", shared: false };
const EMPTY_PROBLEM_FORM = {
  title: "",
  link: "",
  problemKey: "",
  rating: "",
  tags: "",
  note: "",
};

export default function ProblemListsPage({ sharedOnly = false }) {
  const { isAdmin, user } = useAuth();
  const [lists, setLists] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const detailRequest = useRef(0);
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [listDialogOpen, setListDialogOpen] = useState(false);
  const [editingList, setEditingList] = useState(null);
  const [listForm, setListForm] = useState(EMPTY_LIST_FORM);
  const [problemDialogOpen, setProblemDialogOpen] = useState(false);
  const [editingProblem, setEditingProblem] = useState(null);
  const [problemForm, setProblemForm] = useState(EMPTY_PROBLEM_FORM);

  const myLists = useMemo(() => lists.filter((item) => sharedOnly ? item.collaborative : item.owner), [lists, sharedOnly]);
  const sharedLists = useMemo(() => lists.filter((item) => item.shared && !item.collaborative), [lists]);

  async function loadLists(preferredId) {
    const response = await listProblemLists();
    const data = visibleProblemLists(requireData(response), sharedOnly);
    setLists(data || []);
    const candidate = preferredId ?? selectedId;
    const nextId = data.some((item) => item.id === candidate)
      ? candidate
      : data[0]?.id ?? null;
    setSelectedId(nextId);
    return nextId;
  }

  async function loadDetail(id) {
    const request = ++detailRequest.current;
    if (!id) {
      setDetail(null);
      setDetailLoading(false);
      return;
    }
    setDetailLoading(true);
    try {
      const response = await getProblemList(id);
      if (request === detailRequest.current) setDetail(requireData(response));
    } catch (requestError) {
      if (request === detailRequest.current) {
        setError(requestError.message || "题单加载失败");
        setDetail(null);
      }
    } finally {
      if (request === detailRequest.current) setDetailLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    async function initialize() {
      try {
        const response = await listProblemLists();
        if (!active) return;
        const data = visibleProblemLists(requireData(response), sharedOnly);
        setLists(data);
        setSelectedId(data[0]?.id ?? null);
      } catch (requestError) {
        if (active) setError(requestError.message || "题单加载失败");
      } finally {
        if (active) setLoading(false);
      }
    }
    initialize();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    loadDetail(selectedId);
  }, [selectedId]);

  function openCreateList() {
    setEditingList(null);
    setListForm({ ...EMPTY_LIST_FORM, shared: sharedOnly });
    setError("");
    setListDialogOpen(true);
  }

  function openEditList() {
    if (!detail?.list?.owner) return;
    setEditingList(detail.list);
    setListForm({
      name: detail.list.name || "",
      description: detail.list.description || "",
      shared: Boolean(detail.list.shared),
    });
    setError("");
    setListDialogOpen(true);
  }

  async function submitList(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const response = editingList
        ? await updateProblemList(editingList.id, { ...listForm, shared: sharedOnly || listForm.shared })
        : await createProblemList({ ...listForm, shared: sharedOnly || listForm.shared });
      const nextDetail = requireData(response);
      setListDialogOpen(false);
      const nextId = await loadLists(nextDetail.list.id);
      if (nextId === nextDetail.list.id) setDetail(nextDetail);
      else await loadDetail(nextId);
    } catch (requestError) {
      setError(requestError.message || "题单保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function removeList() {
    if (!detail?.list?.owner) return;
    if (!window.confirm(`确定删除题单“${detail.list.name}”及其中的全部题目吗？`)) return;
    setSaving(true);
    setError("");
    try {
      requireData(await deleteProblemList(detail.list.id));
      setDetail(null);
      await loadLists(null);
    } catch (requestError) {
      setError(requestError.message || "题单删除失败");
    } finally {
      setSaving(false);
    }
  }

  function openAddProblem() {
    setEditingProblem(null);
    setProblemForm(EMPTY_PROBLEM_FORM);
    setError("");
    setProblemDialogOpen(true);
  }

  function openEditProblem(problem) {
    setEditingProblem(problem);
    setProblemForm({
      title: problem.title || "",
      link: problem.link || "",
      problemKey: problem.problemKey || "",
      rating: problem.rating ?? "",
      tags: problem.tags || "",
      note: problem.note || "",
    });
    setError("");
    setProblemDialogOpen(true);
  }

  async function submitProblem(event) {
    event.preventDefault();
    if (!detail || !canAddProblem(detail.list)) return;
    setSaving(true);
    setError("");
    const payload = {
      ...problemForm,
      rating: problemForm.rating === "" ? null : Number(problemForm.rating),
    };
    try {
      const response = editingProblem
        ? await updateProblemListItem(detail.list.id, editingProblem.id, payload)
        : await addProblemListItem(detail.list.id, payload);
      requireData(response);
      setProblemDialogOpen(false);
      await Promise.all([loadDetail(detail.list.id), loadLists(detail.list.id)]);
    } catch (requestError) {
      setError(requestError.message || "题目保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function removeProblem(problem) {
    if (!window.confirm(`确定从题单中移除“${problem.title}”吗？`)) return;
    setSaving(true);
    setError("");
    try {
      requireData(await deleteProblemListItem(detail.list.id, problem.id));
      await Promise.all([loadDetail(detail.list.id), loadLists(detail.list.id)]);
    } catch (requestError) {
      setError(requestError.message || "题目移除失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="problem-lists-page stack-y-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 text-2xl font-bold tracking-tight text-text-primary">{sharedOnly ? "共享题单" : "我的题单"}</h1>
        </div>
        {(!sharedOnly || isAdmin) && <button type="button" className="button-primary inline-flex items-center gap-2" onClick={openCreateList}>
          <FolderPlus size={16} />创建题单
        </button>}
      </header>

      {error && (
        <div className="flex items-start justify-between gap-3 rounded-xl border border-[#e3c6c6] bg-[#fff5f5] px-4 py-3 text-sm text-error">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")} className="border-0 bg-transparent text-error"><X size={15} /></button>
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-3">
        <StatCard label={sharedOnly ? "公共投稿" : "我的题单"} value={sharedOnly ? myLists.reduce((sum,item)=>sum+item.problemCount,0) : myLists.length} icon={FolderOpen} />
        <StatCard label={sharedOnly ? "专题题单" : "收藏题目"} value={sharedOnly ? sharedLists.length : myLists.reduce((sum, item) => sum + item.problemCount, 0)} icon={FileText} />
        <StatCard label="题目总数" value={lists.reduce((sum,item)=>sum+item.problemCount,0)} icon={Globe2} />
      </section>

      <section className="grid min-h-[560px] gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
        <aside className="rounded-2xl border border-border bg-white p-3 shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
          {loading ? (
            <div className="stack-y-2 p-1 animate-pulse">
              {Array.from({ length: 4 }).map((_, index) => <div key={index} className="h-20 rounded-xl bg-bg-secondary" />)}
            </div>
          ) : (
            <>
              <ListGroup
                title={sharedOnly ? "公共题单" : "我的题单"}
                items={myLists}
                selectedId={selectedId}
                onSelect={setSelectedId}
                empty="还没有个人题单"
              />
              {sharedOnly && <>
              <div className="my-3 border-t border-border" />
              <ListGroup
                title="专题分享 · 只读"
                items={sharedLists}
                selectedId={selectedId}
                onSelect={setSelectedId}
                empty="暂无共享题单"
              />
              </>}
            </>
          )}
        </aside>

        <main className="overflow-hidden rounded-2xl border border-border bg-white shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
          {detailLoading ? (
            <div className="p-6 animate-pulse">
              <div className="h-7 w-52 rounded-sm bg-bg-secondary" />
              <div className="mt-3 h-4 w-96 max-w-full rounded-sm bg-bg-secondary" />
              <div className="mt-8 stack-y-3">{Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-28 rounded-xl bg-bg-secondary" />)}</div>
            </div>
          ) : detail ? (
            <>
              <div className="border-b border-border p-5 sm:p-6">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="m-0 text-xl font-bold text-text-primary">{detail.list.name}</h2>
                      <VisibilityBadge shared={detail.list.shared} collaborative={detail.list.collaborative} />
                    </div>
                    {detail.list.description && <p className="m-0 mt-2 max-w-3xl text-sm leading-6 text-text-secondary">{detail.list.description}</p>}
                    <p className="m-0 mt-3 text-[11px] text-text-secondary">
                      {!detail.list.collaborative && `${detail.list.ownerDisplayName || detail.list.ownerUsername} · `}{detail.list.problemCount} 道题
                    </p>
                  </div>
                  {detail.list.owner && !detail.list.collaborative && (
                    <div className="flex items-center gap-2">
                      <button type="button" onClick={openEditList} className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-border bg-white px-3 text-xs font-bold hover:bg-bg-secondary">
                        <Pencil size={14} />编辑
                      </button>
                      <button type="button" onClick={removeList} disabled={saving} className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#ecd1d1] bg-white px-3 text-xs font-bold text-error hover:bg-[#fff5f5]">
                        <Trash2 size={14} />删除
                      </button>
                    </div>
                  )}
                </div>
              </div>

              <div className="p-5 sm:p-6">
                <div className="mb-4 flex items-center justify-between gap-3">
                  <h3 className="m-0 text-base font-bold text-text-primary">题目</h3>
                  {canAddProblem(detail.list) && (
                    <button type="button" onClick={openAddProblem} className="button-primary inline-flex items-center gap-2">
                      <Plus size={15} />添加题目
                    </button>
                  )}
                </div>

                {detail.problems.length === 0 ? (
                  <EmptyState title="题单还是空的" />
                ) : (
                  <div className="stack-y-3">
                    {detail.problems.map((problem, index) => (
                      <ProblemRow
                        key={problem.id}
                        problem={problem}
                        index={index + 1}
                        editable={canEditProblem(detail.list, problem, user?.id, isAdmin)}
                        onEdit={() => openEditProblem(problem)}
                        onDelete={() => removeProblem(problem)}
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="flex min-h-[520px] items-center justify-center p-6">
              <EmptyState title="还没有题单" />
            </div>
          )}
        </main>
      </section>

      <ListEditorDialog
        open={listDialogOpen}
        onOpenChange={(open) => !saving && setListDialogOpen(open)}
        editing={Boolean(editingList)}
        form={listForm}
        setForm={setListForm}
        sharedOnly={sharedOnly}
        isAdmin={isAdmin}
        saving={saving}
        error={error}
        onSubmit={submitList}
      />
      <ProblemEditorDialog
        open={problemDialogOpen}
        onOpenChange={(open) => !saving && setProblemDialogOpen(open)}
        editing={Boolean(editingProblem)}
        form={problemForm}
        setForm={setProblemForm}
        saving={saving}
        error={error}
        onSubmit={submitProblem}
      />
    </div>
  );
}

function requireData(response) {
  if (response?.code !== 200) throw new Error(response?.message || "请求失败");
  return response.data;
}

function StatCard({ label, value, icon: Icon }) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-border bg-white p-4 shadow-[0_10px_30px_rgba(17,17,17,0.03)]">
      <div><p className="m-0 text-xs font-semibold text-text-secondary">{label}</p><strong className="mt-1 block text-2xl text-text-primary">{value}</strong></div>
      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#eef3f8] text-[#17324f]"><Icon size={19} /></span>
    </div>
  );
}

function ListGroup({ title, items, selectedId, onSelect, empty }) {
  return (
    <div>
      <p className="m-0 px-2 py-2 font-mono text-[10px] font-bold tracking-[0.15em] text-text-secondary">{title}</p>
      {items.length === 0 ? <p className="m-0 px-2 py-3 text-xs text-text-secondary">{empty}</p> : (
        <div className="stack-y-1">
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelect(item.id)}
              className={`w-full rounded-xl border px-3 py-3 text-left transition-colors ${selectedId === item.id ? "border-[#17324f] bg-[#eef3f8]" : "border-transparent bg-white hover:bg-bg-secondary"}`}
            >
              <span className="flex items-center justify-between gap-2">
                <strong className="truncate text-sm text-text-primary">{item.name}</strong>
                {item.shared ? <Globe2 size={13} className="shrink-0 text-[#2b6f9e]" /> : <LockKeyhole size={13} className="shrink-0 text-text-secondary" />}
              </span>
              <small className="mt-1 block truncate text-xs text-text-secondary">{item.problemCount} 道题{!item.collaborative && ` · ${item.owner ? "我创建的" : item.ownerDisplayName || item.ownerUsername}`}</small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function VisibilityBadge({ shared, collaborative }) {
  if (collaborative) return <span className="list-mode-badge">公共投稿</span>;
  return shared ? (
    <span className="inline-flex items-center gap-1 rounded-full bg-[#e7f3fb] px-2 py-1 text-[10px] font-bold text-[#245b8f]"><Globe2 size={11} />只读分享</span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded-full bg-bg-secondary px-2 py-1 text-[10px] font-bold text-text-secondary"><LockKeyhole size={11} />仅自己</span>
  );
}

function ProblemRow({ problem, index, editable, onEdit, onDelete }) {
  const tags = String(problem.tags || "").split(",").map((tag) => tag.trim()).filter(Boolean);
  return (
    <article className="rounded-xl border border-border bg-[#fdfdfb] p-4 transition-colors hover:border-[#b9c7d6]">
      <div className="flex items-start gap-3">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#17324f] font-mono text-xs font-bold text-white">{String(index).padStart(2, "0")}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <a href={problem.link} target="_blank" rel="noreferrer" className="inline-flex max-w-full items-center gap-1.5 font-bold text-text-primary hover:text-[#245b8f] hover:underline">
                <span className="truncate">{problem.title}</span><ExternalLink size={13} className="shrink-0" />
              </a>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-[10px] text-text-secondary">
                {problem.problemKey && <span className="font-mono font-bold">{problem.problemKey}</span>}
                {problem.rating != null && <span>Rating {problem.rating}</span>}
              </div>
            </div>
            <AddToProblemListButton problem={problem} compact initialNote={problem.note || ""} />
            {editable && (
              <div className="flex shrink-0 items-center gap-1">
                <button type="button" onClick={onEdit} className="rounded-lg p-2 text-text-secondary hover:bg-white hover:text-text-primary" aria-label="编辑题目"><Pencil size={14} /></button>
                <button type="button" onClick={onDelete} className="rounded-lg p-2 text-text-secondary hover:bg-[#fff0f0] hover:text-error" aria-label="移除题目"><Trash2 size={14} /></button>
              </div>
            )}
          </div>
          {tags.length > 0 && <div className="mt-2 flex flex-wrap gap-1.5">{tags.map((tag) => <span key={tag} className="rounded-md bg-white px-2 py-1 text-[10px] text-text-secondary ring-1 ring-border">{tag}</span>)}</div>}
          {problem.note && <p className="m-0 mt-3 border-l-2 border-[#b7c8d9] pl-3 text-xs leading-5 text-text-secondary">{problem.note}</p>}
        </div>
      </div>
    </article>
  );
}

function DialogFrame({ open, onOpenChange, title, description, children }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/45 backdrop-blur-[2px]" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-[81] max-h-[90vh] w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-5 shadow-[0_28px_90px_rgba(8,22,39,0.25)] sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div><Dialog.Title className="m-0 text-xl font-bold text-text-primary">{title}</Dialog.Title><Dialog.Description className="sr-only">{description}</Dialog.Description></div>
            <Dialog.Close asChild><button type="button" className="rounded-lg p-2 text-text-secondary hover:bg-bg-secondary" aria-label="关闭"><X size={18} /></button></Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function ListEditorDialog({ open, onOpenChange, editing, form, setForm, sharedOnly, isAdmin, saving, error, onSubmit }) {
  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={editing ? "编辑题单" : "创建题单"} description="题单就是一级分类，例如“区间 DP”或“暑假补题”。">
      <form className="mt-5 stack-y-4" onSubmit={onSubmit}>
        <Field label="题单名称"><input required maxLength={80} value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} placeholder="例如：区间 DP" className="form-input" /></Field>
        <Field label="简介（可选）"><textarea maxLength={500} rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="记录这个题单的训练目标或使用说明" className="form-input resize-none" /></Field>
        {!sharedOnly && isAdmin && (
          <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-bg-secondary p-3">
            <input type="checkbox" checked={form.shared} onChange={(event) => setForm((current) => ({ ...current, shared: event.target.checked }))} className="mt-0.5 h-4 w-4 accent-[#17324f]" />
            <span><strong className="block text-sm text-text-primary">发布为只读题单</strong></span>
          </label>
        )}
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
        <DialogActions saving={saving} submitText={editing ? "保存修改" : "创建题单"} />
      </form>
    </DialogFrame>
  );
}

function ProblemEditorDialog({ open, onOpenChange, editing, form, setForm, saving, error, onSubmit }) {
  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={editing ? "编辑题目" : "添加题目"} description="粘贴标准 Codeforces 链接后，可自动从本地题库补全题目资料。">
      <form className="mt-5 stack-y-4" onSubmit={onSubmit}>
        <Field label="题目链接"><input required maxLength={1000} type="url" value={form.link} onChange={(event) => setForm((current) => ({ ...current, link: event.target.value }))} placeholder="https://codeforces.com/problemset/problem/607/B" className="form-input" /></Field>
        <Field label="题目标题（CF 题库可自动补全）"><input maxLength={255} value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} placeholder="Zuma" className="form-input" /></Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="题目编号（可选）"><input maxLength={64} value={form.problemKey} onChange={(event) => setForm((current) => ({ ...current, problemKey: event.target.value }))} placeholder="607-B" className="form-input" /></Field>
          <Field label="Rating（可选）"><input type="number" min="0" max="5000" value={form.rating} onChange={(event) => setForm((current) => ({ ...current, rating: event.target.value }))} placeholder="1800" className="form-input" /></Field>
        </div>
        <Field label="标签（可选，逗号分隔）"><input maxLength={1000} value={form.tags} onChange={(event) => setForm((current) => ({ ...current, tags: event.target.value }))} placeholder="dp, two pointers" className="form-input" /></Field>
        <Field label="备注（可选）"><textarea maxLength={1000} rows={3} value={form.note} onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} placeholder="记录关键思路、易错点或重做计划" className="form-input resize-none" /></Field>
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
        <DialogActions saving={saving} submitText={editing ? "保存修改" : "加入题单"} />
      </form>
    </DialogFrame>
  );
}

function Field({ label, children }) {
  return <label className="block"><span className="mb-1.5 block text-xs font-bold text-text-primary">{label}</span>{children}</label>;
}

function DialogActions({ saving, submitText }) {
  return (
    <div className="flex justify-end gap-2 border-t border-border pt-4">
      <Dialog.Close asChild><button type="button" className="min-h-10 rounded-xl border border-border bg-white px-4 text-sm font-bold hover:bg-bg-secondary">取消</button></Dialog.Close>
      <button type="submit" disabled={saving} className="button-primary min-w-28">{saving ? "保存中..." : submitText}</button>
    </div>
  );
}
