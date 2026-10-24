import { Inbox } from "lucide-react";

export default function EmptyState({ icon, title, description }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon" aria-hidden="true">{icon || <Inbox size={26} />}</div>
      <p className="text-text-primary font-medium m-0">{title || "暂无数据"}</p>
      {description && (
        <p className="text-sm text-text-secondary mt-1 m-0">{description}</p>
      )}
    </div>
  );
}
