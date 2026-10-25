import { ExternalLink, Globe2, LockKeyhole, Pencil, Plus, Trash2 } from "lucide-react";
import { canAddProblem, canEditProblem } from "../../utils/problemListPermissions.js";
import EmptyState from "../ui/EmptyState";
import AddToProblemListButton from "../ui/AddToProblemListButton";
import SurfaceContours from "../ui/SurfaceContours";

export default function ProblemListDetails({
  detailLoading, detail, saving, userId, isAdmin, openEditList, removeList,
  openAddProblem, openEditProblem, removeProblem,
}) {
  return (
        <main className="overflow-hidden rounded-2xl border border-border bg-white shadow-[0_12px_36px_rgba(17,17,17,0.035)]">
          {detailLoading ? (
            <div className="p-6 animate-pulse">
              <div className="h-7 w-52 rounded-sm bg-bg-secondary" />
              <div className="mt-3 h-4 w-96 max-w-full rounded-sm bg-bg-secondary" />
              <div className="mt-8 stack-y-3">{Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-28 rounded-xl bg-bg-secondary" />)}</div>
            </div>
          ) : detail ? (
            <>
              <div className="library-detail-heading surface-contour-host border-b border-border p-5 sm:p-6">
                <SurfaceContours tone="violet" />
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="m-0 text-xl font-bold text-text-primary">{detail.list.name}</h2>
                      <VisibilityBadge shared={detail.list.shared} collaborative={detail.list.collaborative} />
                    </div>
                    {detail.list.description && <p className="m-0 mt-2 max-w-3xl text-sm leading-6 text-text-secondary">{detail.list.description}</p>}
                    <p className="m-0 mt-3 text-[11px] text-text-secondary">
                      {!detail.list.collaborative && (detail.list.ownerDisplayName || detail.list.ownerUsername) ? `${detail.list.ownerDisplayName || detail.list.ownerUsername} · ` : null}{detail.list.problemCount} 道题
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
                        editable={canEditProblem(detail.list, problem, userId, isAdmin)}
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
    <article className="library-problem-row">
        <span className="library-problem-index">{String(index).padStart(2, "0")}</span>
        <div className="library-problem-content">
          <a href={problem.link} target="_blank" rel="noreferrer" className="library-problem-title">
            <span>{problem.title}</span><ExternalLink size={16} aria-hidden="true" />
          </a>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-text-secondary">
            {problem.problemKey && <span className="font-mono font-bold">{problem.problemKey}</span>}
            {problem.rating != null && <span>Rating {problem.rating}</span>}
          </div>
          {tags.length > 0 && <div className="mt-2 flex flex-wrap gap-1.5">{tags.map((tag) => <span key={tag} className="rounded-md bg-white px-2 py-1 text-[10px] text-text-secondary ring-1 ring-border">{tag}</span>)}</div>}
          {problem.note && <p className="m-0 mt-3 border-l-2 border-[#b7c8d9] pl-3 text-xs leading-5 text-text-secondary">{problem.note}</p>}
        </div>
        <div className="library-problem-actions" role="group" aria-label={`${problem.title}的操作`}>
          <AddToProblemListButton problem={problem} compact initialNote={problem.note || ""} />
          {editable && <>
            <button type="button" onClick={onEdit} className="library-icon-button" aria-label="编辑题目" title="编辑题目"><Pencil size={18} aria-hidden="true" /></button>
            <button type="button" onClick={onDelete} className="library-icon-button is-danger" aria-label="移除题目" title="移除题目"><Trash2 size={18} aria-hidden="true" /></button>
          </>}
        </div>
    </article>
  );
}
