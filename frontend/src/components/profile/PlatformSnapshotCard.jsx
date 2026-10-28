import { BookOpenCheck, CheckCircle2, ExternalLink, Trophy } from "lucide-react";
import { platformSnapshotModel } from "../../utils/platformSnapshot";
import { formatPlatformMetric } from "../../utils/platformOverview";
import "./PlatformSnapshotCard.css";

export default function PlatformSnapshotCard({ platform, accountId, overview, compact = false, emptyText }) {
  const model = platformSnapshotModel(platform.key, accountId, overview);
  if (!model) return null;
  const primary = model.metrics[0];
  const metrics = compact ? model.metrics : model.metrics.slice(1);
  const facts = compact ? model.highlights : model.facts;
  return <article className={`platform-snapshot-card${compact ? " is-compact" : ""}`} aria-label={`${platform.label} 账号展示卡`}
    style={{ "--snapshot-accent": model.accent, "--snapshot-tint": model.tint }}>
    <header className="platform-snapshot-header">
      <div className="platform-snapshot-identity">
        <span className="platform-snapshot-mark" aria-hidden="true"><img src={platform.icon} alt="" width="40" height="40"/></span>
        <div className="platform-snapshot-name"><span className="platform-snapshot-platform">{platform.label}</span>
          <h3><a href={model.url} target="_blank" rel="noopener noreferrer">{model.name}<ExternalLink size={17} aria-hidden="true"/></a></h3>
          <div className="platform-snapshot-badges">
            {model.rank && <span className="platform-snapshot-rank" title={`${model.rank} · ${model.rankRange}`}>{model.rank}</span>}
            <span className="platform-snapshot-verified"><CheckCircle2 size={15} aria-hidden="true"/>已绑定</span>
          </div>
          {model.showUid && <span className="platform-snapshot-uid">UID {model.accountId}</span>}
        </div>
      </div>
      {!compact && primary && <dl className="platform-snapshot-primary" aria-label="主要统计"><div><dt>{primary.label}</dt>
        <dd style={primary.color ? {color:primary.color} : undefined}>{formatPlatformMetric(primary.value)}</dd>
      </div></dl>}
    </header>
    {(metrics.length > 0 || !primary || !compact) && <div className="platform-snapshot-content" data-metrics={metrics.length}>
      {metrics.length > 0 ? <dl className="platform-snapshot-metrics" aria-label={`${platform.label} 核心统计`}>
        {metrics.map(metric => <div key={metric.key} className={`snapshot-metric-${metric.key}`}>
          <dt>{metric.key === "solvedCount" ? <BookOpenCheck size={17} aria-hidden="true"/> : <Trophy size={17} aria-hidden="true"/>}{metric.label}</dt>
          <dd style={metric.color ? {color:metric.color} : undefined}>{formatPlatformMetric(metric.value)}</dd>
          {metric.rank && <small>{metric.rank}</small>}
        </div>)}
      </dl> : !primary && <p className="platform-snapshot-empty" role="status">{emptyText}</p>}
      {!compact && <dl className="platform-snapshot-facts" aria-label="平台账号资料">
        <div><dt>{["luogu","nowcoder"].includes(platform.key) ? "平台 UID" : "平台账号"}</dt><dd>{model.accountId}</dd></div>
        <div><dt>数据来源</dt><dd>{model.source}</dd></div>
      </dl>}
    </div>}
    {facts.length>0 && <dl className="platform-snapshot-public" aria-label={`${platform.label} 公开资料`}>
      {facts.map(fact=><div key={fact.key} data-field={fact.key}><dt>{fact.label}</dt><dd>{fact.value}</dd></div>)}
    </dl>}
  </article>;
}
