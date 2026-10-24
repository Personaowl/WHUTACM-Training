export function PageIcon({ icon: Icon, tone = "blue", compact = false }) {
  return (
    <span className={`page-icon${compact ? " is-compact" : ""}`} data-tone={tone} aria-hidden="true">
      <Icon size={compact ? 19 : 24} strokeWidth={1.8} />
    </span>
  );
}

export default function PageHeading({ icon, title, tone = "blue" }) {
  return (
    <div className="page-heading-identity">
      <PageIcon icon={icon} tone={tone} />
      <h1 className="page-title">{title}</h1>
    </div>
  );
}
