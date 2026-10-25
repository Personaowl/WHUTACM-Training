import { Globe2, LockKeyhole } from "lucide-react";

export default function ProblemListSidebar({ loading, sharedOnly, myLists, sharedLists, selectedId, onSelect }) {
  return (
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
                onSelect={onSelect}
                empty="还没有个人题单"
              />
              {sharedOnly && <>
              <div className="my-3 border-t border-border" />
              <ListGroup
                title="专题分享 · 只读"
                items={sharedLists}
                selectedId={selectedId}
                onSelect={onSelect}
                empty="暂无共享题单"
              />
              </>}
            </>
          )}
        </aside>
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
              aria-pressed={selectedId === item.id}
              className={`library-select w-full rounded-xl border px-3 py-3 text-left transition-colors ${selectedId === item.id ? "border-[#17324f] bg-[#eef3f8]" : "border-transparent bg-white hover:bg-bg-secondary"}`}
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
