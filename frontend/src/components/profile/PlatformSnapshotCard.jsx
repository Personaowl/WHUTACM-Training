import { BookOpenCheck, Building2, CheckCircle2, ExternalLink, MapPin, Trophy, UserRound } from "lucide-react";
import { platformSnapshotModel, platformStatisticRows } from "../../utils/platformSnapshot";
import { formatPlatformMetric } from "../../utils/platformOverview";
import "./PlatformSnapshotCard.css";

function PublicFacts({ facts, variant, platform, label }) {
  if (!facts.length) return null;
  const labels = { context: "公开简介", statistics: "训练与社区统计", records: "公开账号资料", highlights: "账号统计" };
  return <dl className={`platform-snapshot-public is-${variant}`} aria-label={`${platform} ${label || labels[variant]}`}
    style={variant === "statistics" ? { "--snapshot-stat-columns": facts.length } : undefined}>
    {facts.map(fact => <div key={fact.key} data-field={fact.key}>
      <dt>{variant === "context" && (fact.key === "organization" ? <Building2 size={16} aria-hidden="true" /> : <MapPin size={16} aria-hidden="true" />)}
        <span>{fact.label}</span></dt><dd>{fact.value}</dd>
    </div>)}
  </dl>;
}

function StatisticFacts({ facts, platform }) {
  const rows = platformStatisticRows(facts);
  if (!rows.length) return null;
  return <div className="platform-snapshot-statistics">
    {rows.map(row => <PublicFacts key={row.key} facts={row.facts} variant="statistics" platform={platform} label={row.label} />)}
  </div>;
}

export default function PlatformSnapshotCard({ platform, accountId, overview, compact = false, emptyText }) {
  const model = platformSnapshotModel(platform.key, accountId, overview);
  if (!model) return null;
  const primary = model.metrics[0];
  const metrics = compact ? model.metrics : model.metrics.slice(1);
  const { identity, statistics, records } = model.factGroups;
  const showPerformance = metrics.length > 0 || !primary || (compact ? model.highlights.length : statistics.length) > 0;

  return <article className={`platform-snapshot-card${compact ? " is-compact" : ""}`} aria-label={`${platform.label} 账号展示卡`}
    style={{ "--snapshot-accent": model.accent, "--snapshot-tint": model.tint }}>
    <header className="platform-snapshot-header">
      <div className="platform-snapshot-person">
        <div className="platform-snapshot-identity">
          <span className="platform-snapshot-mark" aria-hidden="true"><img src={platform.icon} alt="" width="40" height="40" /></span>
          <div className="platform-snapshot-name">
            <span className="platform-snapshot-platform">{platform.label}</span>
            <h3><a href={model.url} target="_blank" rel="noopener noreferrer">{model.name}<ExternalLink size={17} aria-hidden="true" /></a></h3>
            <div className="platform-snapshot-badges">
              {model.rank && <span className="platform-snapshot-rank" title={`${model.rank} · ${model.rankRange}`}>{model.rank}</span>}
              <span className="platform-snapshot-verified"><CheckCircle2 size={15} aria-hidden="true" />已绑定</span>
            </div>
            {model.showUid && <span className="platform-snapshot-uid">UID {model.accountId}</span>}
          </div>
        </div>
        {!compact && <PublicFacts facts={identity} variant="context" platform={platform.label} />}
      </div>
      {!compact && primary && <dl className="platform-snapshot-primary" aria-label="主要统计"><div>
        <dt>{primary.label}</dt><dd style={primary.color ? { color: primary.color } : undefined}>{formatPlatformMetric(primary.value)}</dd>
      </div></dl>}
    </header>

    <div className="platform-snapshot-content" data-metrics={metrics.length} data-performance={showPerformance}>
      {showPerformance && <div className="platform-snapshot-performance">
        {metrics.length > 0 && <dl className="platform-snapshot-metrics" aria-label={`${platform.label} 核心统计`}>
          {metrics.map(metric => <div key={metric.key} className={`snapshot-metric-${metric.key}`}>
            <dt>{metric.key === "solvedCount" ? <BookOpenCheck size={18} aria-hidden="true" /> : <Trophy size={18} aria-hidden="true" />}{metric.label}</dt>
            <dd style={metric.color ? { color: metric.color } : undefined}>{formatPlatformMetric(metric.value)}</dd>
            {metric.rank && <small>{metric.rank}</small>}
          </div>)}
        </dl>}
        {!primary && <p className="platform-snapshot-empty" role="status">{emptyText}</p>}
        {compact ? <PublicFacts facts={model.highlights} variant="highlights" platform={platform.label} />
          : <StatisticFacts facts={statistics} platform={platform.label} />}
      </div>}

      {!compact && <section className="platform-snapshot-dossier" aria-label="平台账号档案">
        <h4><UserRound size={18} aria-hidden="true" />账号档案</h4>
        <dl className="platform-snapshot-facts" aria-label="平台账号资料"><div>
          <dt>{["luogu", "nowcoder"].includes(platform.key) ? "平台 UID" : "平台账号"}</dt><dd>{model.accountId}</dd>
        </div></dl>
        <PublicFacts facts={records} variant="records" platform={platform.label} />
        <dl className="platform-snapshot-source"><div><dt>数据来源</dt><dd>{model.source}</dd></div></dl>
      </section>}
    </div>
  </article>;
}
