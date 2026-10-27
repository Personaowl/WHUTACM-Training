import { useId, useMemo, useState } from "react";
import { difficultyColor, ratingDistribution, tagDistribution, TAG_COLORS } from "../../utils/codeforcesCharts";
import "./CodeforcesCharts.css";

const number = value => value.toLocaleString("zh-CN");
const point = (radius, angle) => ({ x: 220 + radius * Math.cos(angle), y: 180 + radius * Math.sin(angle) });

export function TagSolvedChart({ items = [] }) {
  const id = useId();
  const [active, setActive] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const tags = useMemo(() => tagDistribution(items), [items]);
  const total = tags.reduce((sum, item) => sum + item.count, 0);
  const selected = tags.find(item => item.tag === active);
  let cursor = -Math.PI / 2;
  const arcs = tags.map((item, index) => {
    const sweep = item.count / total * Math.PI * 2;
    const gap = Math.min(0.045, sweep * 0.16);
    const start = point(100, cursor + gap / 2);
    const end = point(100, cursor + sweep - gap / 2);
    const angle = cursor + sweep / 2;
    cursor += sweep;
    return { ...item, index, angle, path: `M ${start.x} ${start.y} A 100 100 0 ${sweep - gap > Math.PI ? 1 : 0} 1 ${end.x} ${end.y}` };
  });
  const labels = [-1, 1].flatMap(side => {
    const entries = arcs.slice(0, 6).filter(arc => (Math.cos(arc.angle) >= 0 ? 1 : -1) === side)
      .sort((a, b) => Math.sin(a.angle) - Math.sin(b.angle));
    return entries.map((arc, index) => ({ ...arc, side, labelY: entries.length === 1
      ? Math.max(60, Math.min(300, point(125, arc.angle).y))
      : 66 + (index + 0.5) * (224 / entries.length) }));
  });
  function toggle(tag) { setActive(tag); }
  return <section className="cf-chart-card cf-tags-chart" aria-labelledby={`${id}-heading`}>
    <header className="cf-chart-heading"><div><h3 id={`${id}-heading`}>题目标签分布</h3><span>Tags Solved</span></div>
      <span className="cf-chart-badge">{tags.length ? `${items.length} 类标签` : "暂无数据"}</span></header>
    {!total ? <p className="cf-chart-empty">暂无已解题目标签</p> : <>
      <div className="cf-tag-visual">
        <svg viewBox="0 0 440 360" aria-hidden="true">
          <circle cx="220" cy="180" r="100" fill="none" stroke="var(--cf-chart-track)" strokeWidth="30" />
          {arcs.map(arc => <path key={arc.tag} d={arc.path} fill="none" stroke={TAG_COLORS[arc.index]} strokeWidth={active === arc.tag ? 35 : 29}
            strokeLinecap="butt" className="cf-tag-arc" opacity={active && active !== arc.tag ? 0.45 : 1}
            onPointerEnter={() => setActive(arc.tag)} onPointerLeave={() => setActive(null)} onClick={() => toggle(arc.tag)} />)}
          {labels.map(arc => {
            const start = point(120, arc.angle);
            const elbowX = arc.side > 0 ? 350 : 90;
            const labelX = arc.side > 0 ? 356 : 84;
            return <g key={arc.tag} className="cf-tag-callout" opacity={active && active !== arc.tag ? 0.45 : 1}>
              <polyline points={`${start.x},${start.y} ${elbowX - arc.side * 10},${arc.labelY} ${elbowX},${arc.labelY}`} fill="none" stroke={TAG_COLORS[arc.index]} strokeWidth="1.8" />
              <text x={labelX} y={arc.labelY - 5} textAnchor={arc.side > 0 ? "start" : "end"}>{arc.tag.length > 11 ? `${arc.tag.slice(0, 10)}…` : arc.tag}</text>
              <text x={labelX} y={arc.labelY + 13} textAnchor={arc.side > 0 ? "start" : "end"} className="cf-tag-callout-count">{number(arc.count)} · {(arc.count / total * 100).toFixed(1)}%</text>
            </g>;
          })}
        </svg>
        <div className="cf-tag-center" aria-live="polite"><span>{selected?.tag || "标签命中次数"}</span><strong>{number(selected?.count ?? total)}</strong>
          <small>{selected ? `${(selected.count / total * 100).toFixed(1)}%` : "一题可包含多个标签"}</small></div>
      </div>
      <div className="cf-tag-legend">
        {(expanded ? tags : tags.slice(0, 6)).map((item, index) => <button type="button" key={item.tag} title={item.tag}
          aria-label={`${item.tag}：${item.count}，${(item.count / total * 100).toFixed(1)}%`}
          aria-pressed={active === item.tag} onPointerEnter={() => setActive(item.tag)} onPointerLeave={() => setActive(null)}
          onFocus={() => setActive(item.tag)} onBlur={() => setActive(null)} onClick={() => toggle(item.tag)}>
          <i style={{ background: TAG_COLORS[index] }} aria-hidden="true" /><span>{item.tag}</span><strong>{number(item.count)}</strong><small>{(item.count / total * 100).toFixed(1)}%</small>
        </button>)}
      </div>
      {tags.length > 6 && <button type="button" className="cf-chart-expand" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>{expanded ? "收起标签" : "查看其余标签"}</button>}
    </>}
  </section>;
}

export function RatingSolvedChart({ items, stale = false }) {
  const id = useId();
  const [active, setActive] = useState(null);
  const chart = useMemo(() => ratingDistribution(items), [items]);
  const selection = chart?.bars.find(item => item.rating === active);
  const ready = chart && chart.rated > 0;
  const width = Math.max(860, (chart?.bars.length || 28) * 27 + 68);
  const plotWidth = width - 68;
  return <section className="cf-chart-card cf-ratings-chart" aria-labelledby={`${id}-heading`}>
    <header className="cf-chart-heading"><div><h3 id={`${id}-heading`}>已解题目难度分布</h3><span>Problem Ratings</span></div>
      {chart && <span className="cf-chart-badge">{number(chart.rated)} 题有难度</span>}</header>
    {!ready ? <p className="cf-chart-empty">{!chart ? (stale ? "难度分布正在后台同步，可稍后点击“同步 CF”。" : "同步 CF 后查看难度分布。") : "暂无带公开难度的已解题目"}</p> : <>
      <div className="cf-rating-caption" aria-live="polite">{selection ? <><strong>{selection.rating}</strong><span>已解决 {number(selection.count)} 题</span></>
        : <><span>最常解决的难度</span><strong>{chart.peak.rating}</strong><span>· {number(chart.peak.count)} 题</span></>}</div>
      <div className="cf-rating-scroll" tabIndex={0} role="region" aria-label="难度柱状图，可横向滚动；聚焦或点击柱子查看数量">
        <svg viewBox={`0 0 ${width} 310`} className="cf-rating-svg" role="group" aria-label="按题目难度统计的去重 AC 题数">
          <defs>{chart.bars.map(item => <linearGradient id={`${id}-${item.rating}`} key={item.rating} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={difficultyColor(item.rating)} /><stop offset="100%" stopColor={difficultyColor(item.rating)} stopOpacity="0.25" />
          </linearGradient>)}</defs>
          {[0, 1, 2, 3, 4].map(index => {
            const y = 258 - index * 57;
            return <g key={index} className="cf-rating-grid"><line x1="48" x2={width - 16} y1={y} y2={y} /><text x="37" y={y + 5} textAnchor="end">{number(Math.round(chart.ceiling * index / 4))}</text></g>;
          })}
          {chart.bars.map((item, index) => {
            const step = plotWidth / chart.bars.length;
            const x = 48 + index * step + 4;
            const height = item.count / chart.ceiling * 228;
            return <g key={item.rating} role="button" tabIndex={0} aria-label={`难度 ${item.rating}，已解决 ${item.count} 题`}
              aria-pressed={active === item.rating} className="cf-rating-bar" onPointerEnter={() => setActive(item.rating)} onPointerLeave={() => setActive(null)}
              onFocus={() => setActive(item.rating)} onBlur={() => setActive(null)} onClick={() => setActive(item.rating)}
              onKeyDown={event => { if (["Enter", " "].includes(event.key)) { event.preventDefault(); setActive(item.rating); } if (event.key === "Escape") setActive(null); }}>
              <rect x={x - 2} y="24" width={step - 4} height="264" fill="transparent" className="cf-rating-hit" />
              <rect x={x} y={258 - height} width={step - 8} height={height} rx="6" fill={`url(#${id}-${item.rating})`} opacity={active !== null && active !== item.rating ? 0.45 : 1} />
              {index % 2 === 0 && <text x={x + (step - 8) / 2} y="282" textAnchor="middle">{item.rating}</text>}
            </g>;
          })}
        </svg>
      </div>
    </>}
    {chart && <footer className="cf-rating-footer"><span>每道 AC 题仅计一次</span><span>无公开难度 {number(chart.unrated)} 题</span></footer>}
  </section>;
}
