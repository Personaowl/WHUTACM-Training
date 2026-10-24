import { getRatingMeta, parseTags } from "../../utils/cf";
import { ArrowUpRight } from "lucide-react";

export default function ProblemCard({ problem, showTags = true, actions = null }) {
  if (!problem) return null;
  const meta = getRatingMeta(problem.rating);
  const tags = parseTags(problem.tags);

  return (
    <article className="problem-card bg-white border border-border rounded-ui p-4" data-slot={problem.slot?.toLowerCase() || problem.type?.toLowerCase()}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-text-primary m-0 break-words">
            {problem.contestId}
            {problem.problemIndex}. {problem.name}
          </h3>
          {problem.date && (
            <p className="text-xs text-text-secondary mt-1 m-0">
              {problem.date}{problem.type ? ` · ${problem.type}` : ""}
            </p>
          )}
        </div>
        <span
          className="problem-rating text-sm font-bold shrink-0"
          style={{ color: meta.color }}
        >
          {problem.rating ?? "未定级"}
        </span>
      </div>
      {showTags && tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mt-2.5">
          {tags.map((t) => (
            <span
              key={t}
              className="problem-tag text-xs px-2 py-0.5 rounded-full bg-bg-secondary text-text-secondary"
            >
              {t}
            </span>
          ))}
        </div>
      )}
      {(problem.sourceUrl || actions) && (
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
          {problem.sourceUrl ? (
            <a
              className="inline-flex items-center gap-1 text-xs text-text-secondary hover:text-text-primary"
              href={problem.sourceUrl}
              target="_blank"
              rel="noreferrer"
            >
              在 Codeforces 打开 <ArrowUpRight size={15} aria-hidden="true" />
            </a>
          ) : <span />}
          {actions && <div className="flex flex-wrap items-center justify-end gap-2">{actions}</div>}
        </div>
      )}
    </article>
  );
}
