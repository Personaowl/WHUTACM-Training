import { FileText, FolderOpen, FolderPlus, Globe2, BookOpenText, BookCopy, X } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import useProblemLists from "../hooks/useProblemLists";
import PageHeading from "../components/ui/PageHeading";
import ProblemListSidebar from "../components/problem-lists/ProblemListSidebar";
import ProblemListDetails from "../components/problem-lists/ProblemListDetails";
import { ListEditorDialog, ProblemEditorDialog } from "../components/problem-lists/ProblemListDialogs";

export default function ProblemListsPage({ sharedOnly = false }) {
  const { isAdmin, user } = useAuth();
  const {
    lists, myLists, sharedLists, selectedId, setSelectedId, detail, loading, detailLoading,
    saving, error, setError, openCreateList, openEditList, removeList,
    openAddProblem, openEditProblem, removeProblem, listEditor, problemEditor,
  } = useProblemLists(sharedOnly);

  return (
    <div className="library-page stack-y-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <PageHeading icon={sharedOnly ? BookCopy : BookOpenText} title={sharedOnly ? "共享题单" : "我的题单"} tone={sharedOnly ? "mint" : "violet"} />
        {(!sharedOnly || isAdmin) && <button type="button" className="button-primary inline-flex items-center gap-2" onClick={openCreateList}>
          <FolderPlus size={16} />创建题单
        </button>}
      </header>

      {error && (
        <div className="flex items-start justify-between gap-3 rounded-xl border border-[#e3c6c6] bg-[#fff5f5] px-4 py-3 text-sm text-error">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")} aria-label="关闭错误提示" className="border-0 bg-transparent text-error"><X size={15} /></button>
        </div>
      )}

      <section className="library-stats grid gap-3 sm:grid-cols-3">
        <StatCard label={sharedOnly ? "公共投稿" : "我的题单"} value={sharedOnly ? myLists.reduce((sum,item)=>sum+item.problemCount,0) : myLists.length} icon={FolderOpen} />
        <StatCard label={sharedOnly ? "专题题单" : "收藏题目"} value={sharedOnly ? sharedLists.length : myLists.reduce((sum, item) => sum + item.problemCount, 0)} icon={FileText} />
        <StatCard label="题目总数" value={lists.reduce((sum,item)=>sum+item.problemCount,0)} icon={Globe2} />
      </section>

      <section className="library-workspace grid min-h-[560px] gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
        <ProblemListSidebar
          loading={loading} sharedOnly={sharedOnly} myLists={myLists} sharedLists={sharedLists}
          selectedId={selectedId} onSelect={setSelectedId}
        />

        <ProblemListDetails
          detailLoading={detailLoading} detail={detail} saving={saving} userId={user?.id} isAdmin={isAdmin}
          openEditList={openEditList} removeList={removeList} openAddProblem={openAddProblem}
          openEditProblem={openEditProblem} removeProblem={removeProblem}
        />
      </section>

      <ListEditorDialog {...listEditor} isAdmin={isAdmin} />
      <ProblemEditorDialog {...problemEditor} />
    </div>
  );
}

function StatCard({ label, value, icon: Icon }) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-border bg-white p-4 shadow-[0_10px_30px_rgba(17,17,17,0.03)]">
      <div><p className="m-0 text-xs font-semibold text-text-secondary">{label}</p><strong className="mt-1 block text-2xl text-text-primary">{value}</strong></div>
      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#eef3f8] text-[#17324f]" aria-hidden="true"><Icon size={22} strokeWidth={2.2} /></span>
    </div>
  );
}
