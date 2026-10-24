import { useEffect, useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { BookmarkPlus, Check, FolderPlus, LoaderCircle, X } from "lucide-react";
import {
  addProblemListItem,
  createProblemList,
  listProblemLists,
} from "../../api/problemList";

export default function AddToProblemListButton({ problem, compact = false, className = "", initialNote = "" }) {
  const [open, setOpen] = useState(false);
  const [lists, setLists] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [createNew, setCreateNew] = useState(false);
  const [newListName, setNewListName] = useState("");
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  const normalizedProblem = useMemo(() => normalizeProblem(problem), [problem]);

  useEffect(() => {
    if (!open) return undefined;
    let active = true;
    setLoading(true);
    setError("");
    setSaved(false);
    listProblemLists()
      .then((response) => {
        if (!active) return;
        if (response?.code !== 200) throw new Error(response?.message || "题单加载失败");
        const owned = (response.data || []).filter((item) => item.owner || item.collaborative);
        setLists(owned);
        setSelectedId((current) => (
          owned.some((item) => String(item.id) === String(current))
            ? current
            : owned[0]?.id ?? ""
        ));
        setCreateNew(owned.length === 0);
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "题单加载失败");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [open]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!normalizedProblem.link) {
      setError("该题目没有可保存的链接");
      return;
    }
    setSaving(true);
    setError("");
    try {
      let targetListId = selectedId;
      if (createNew) {
        const name = newListName.trim();
        if (!name) throw new Error("请输入新题单名称");
        const createResponse = await createProblemList({ name, description: "", shared: false });
        if (createResponse?.code !== 200) {
          throw new Error(createResponse?.message || "题单创建失败");
        }
        targetListId = createResponse.data?.list?.id;
      }
      if (!targetListId) throw new Error("请选择一个题单");

      const response = await addProblemListItem(targetListId, {
        ...normalizedProblem,
        note: note.trim() || null,
      });
      if (response?.code !== 200) throw new Error(response?.message || "加入题单失败");
      setSaved(true);
      setNewListName("");
      setNote("");
    } catch (requestError) {
      setError(requestError.message || "加入题单失败");
    } finally {
      setSaving(false);
    }
  }

  function handleOpenChange(nextOpen) {
    if (saving) return;
    setOpen(nextOpen);
    if (nextOpen) setNote(initialNote);
    if (!nextOpen) {
      setError("");
      setSaved(false);
      setNote("");
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Trigger asChild>
        <button
          type="button"
          disabled={!normalizedProblem.link}
          className={`${compact ? "problem-list-add is-compact" : "problem-list-add"} ${className}`.trim()}
          aria-label={`将${normalizedProblem.title || "题目"}加入题单`}
            title="加入题单"
        >
            <BookmarkPlus size={18} strokeWidth={2.2} aria-hidden="true" />
          {!compact && <span>加入题单</span>}
        </button>
      </Dialog.Trigger>

      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/40 backdrop-blur-[2px]" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-[81] max-h-[90vh] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-5 shadow-[0_28px_90px_rgba(8,22,39,0.22)] sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <Dialog.Title className="m-0 text-xl font-bold text-text-primary">加入我的题单</Dialog.Title>
              <Dialog.Description className="mt-1 truncate text-xs text-text-secondary">
                {normalizedProblem.title}
              </Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <button type="button" className="rounded-lg border-0 bg-transparent p-2 text-text-secondary hover:bg-bg-secondary" aria-label="关闭">
                <X size={18} />
              </button>
            </Dialog.Close>
          </div>

          {loading ? (
            <div className="flex min-h-40 items-center justify-center text-sm text-text-secondary">
              <LoaderCircle className="mr-2 animate-spin" size={17} />正在读取题单
            </div>
          ) : saved ? (
            <div className="mt-6 rounded-xl border border-[#b9ddc2] bg-[#f2fbf4] px-4 py-5 text-center text-sm font-bold text-success">
              <Check className="mx-auto mb-2" size={24} />已加入题单
              <Dialog.Close asChild>
                <button type="button" className="button-primary mt-4 w-full">完成</button>
              </Dialog.Close>
            </div>
          ) : (
            <form className="mt-5 stack-y-4" onSubmit={handleSubmit}>
              {lists.length > 0 && (
                <div className="grid grid-cols-2 gap-2 rounded-xl bg-bg-secondary p-1">
                  <ModeButton active={!createNew} onClick={() => setCreateNew(false)}>选择题单</ModeButton>
                  <ModeButton active={createNew} onClick={() => setCreateNew(true)}>
                    <FolderPlus size={14} />新建题单
                  </ModeButton>
                </div>
              )}

              {createNew ? (
                <Field label="新题单名称">
                  <input
                    autoFocus
                    required
                    maxLength={80}
                    value={newListName}
                    onChange={(event) => setNewListName(event.target.value)}
                    placeholder="例如：区间 DP"
                    className="form-input"
                  />
                </Field>
              ) : (
                <Field label="选择题单">
                  <select
                    required
                    value={selectedId}
                    onChange={(event) => setSelectedId(event.target.value)}
                    className="form-input"
                  >
                    {lists.map((item) => (
                      <option key={item.id} value={item.id}>{item.name}（{item.shared ? "共享 · " : ""}{item.problemCount} 题）</option>
                    ))}
                  </select>
                </Field>
              )}

              <Field label="备注（可选）">
                <textarea
                  rows={3}
                  maxLength={1000}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="记录关键思路、易错点或重做计划"
                  className="form-input resize-none"
                />
              </Field>

              {error && <p className="m-0 rounded-lg bg-[#fff2f2] px-3 py-2 text-xs text-error">{error}</p>}
              <div className="flex justify-end gap-2 border-t border-border pt-4">
                <Dialog.Close asChild>
                  <button type="button" className="min-h-10 rounded-xl border border-border bg-white px-4 text-sm font-bold hover:bg-bg-secondary">取消</button>
                </Dialog.Close>
                <button type="submit" disabled={saving} className="button-primary min-w-28">
                  {saving ? "保存中..." : createNew ? "创建并加入" : "加入题单"}
                </button>
              </div>
            </form>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function normalizeProblem(problem) {
  const contestId = problem?.contestId;
  const problemIndex = problem?.problemIndex;
  const fallbackKey = contestId && problemIndex ? `${contestId}-${problemIndex}` : null;
  return {
    title: problem?.name || problem?.title || fallbackKey || "未命名题目",
    link: problem?.sourceUrl || problem?.link || "",
    problemKey: problem?.problemKey || fallbackKey,
    rating: problem?.rating ?? null,
    tags: Array.isArray(problem?.tags) ? problem.tags.join(",") : problem?.tags || null,
  };
}

function Field({ label, children }) {
  return <label className="block"><span className="mb-1.5 block text-xs font-bold text-text-primary">{label}</span>{children}</label>;
}

function ModeButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`flex min-h-9 items-center justify-center gap-1.5 rounded-lg border-0 text-xs font-bold transition-colors ${
        active ? "bg-white text-text-primary shadow-xs" : "bg-transparent text-text-secondary hover:text-text-primary"
      }`}
    >
      {children}
    </button>
  );
}
