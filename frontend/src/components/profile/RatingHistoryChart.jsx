import { useEffect, useId, useMemo, useRef, useState } from "react";
import { ExternalLink } from "lucide-react";
import { CF_RATING_BANDS, normalizeRatingHistory, ratingHistoryModel } from "../../utils/ratingHistory";
import "./RatingHistoryChart.css";

const NAMES = { codeforces: "Codeforces", atcoder: "AtCoder", nowcoder: "Nowcoder" };
const COLORS = { codeforces: "#ba7b14", atcoder: "#16877f", nowcoder: "#8056b6" };
const date = second => new Date(second * 1000).toLocaleDateString("zh-CN", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" });
const delta = value => value == null ? "—" : `${value > 0 ? "+" : ""}${value}`;

export default function RatingHistoryChart({ items, platform = "codeforces", pending = false }) {
  const id = useId();
  const refs = useRef([]);
  const canvasRef = useRef(null);
  const [canvasWidth, setCanvasWidth] = useState(920);
  const [range, setRange] = useState("all");
  const [choice, setChoice] = useState(null);
  const [tableOpen, setTableOpen] = useState(false);
  const [page, setPage] = useState(0);
  const all = useMemo(() => normalizeRatingHistory(items, platform), [items, platform]);
  const points = useMemo(() => (all || []).filter(point => range === "all" || point.ratingUpdateTimeSeconds >= Date.now()/1000 - 365*86400), [all, range]);
  const model = useMemo(() => ratingHistoryModel(points), [points]);
  useEffect(() => {
    if (!canvasRef.current) return;
    const element = canvasRef.current;
    const resize = () => setCanvasWidth(Math.max(680, Math.round(element.clientWidth)));
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(element);
    return () => observer.disconnect();
  }, [Boolean(model)]);
  const selectedIndex = Math.max(0, points.findIndex(point => point.contestId === choice) < 0 ? points.length - 1 : points.findIndex(point => point.contestId === choice));
  const selected = points[selectedIndex];
  const width = canvasWidth, height = 350, left = 66, right = 24, top = 24, bottom = 58;
  const plotWidth = width-left-right, plotHeight = height-top-bottom;
  const x = second => left+(second-model.start)/(model.end-model.start)*plotWidth;
  const y = value => top+(model.max-value)/(model.max-model.min)*plotHeight;
  const path = model ? points.map((point, index) => `${index ? "L" : "M"} ${x(point.ratingUpdateTimeSeconds)} ${y(point.newRating)}`).join(" ") : "";
  const pages = Math.ceil(points.length/20), safePage = Math.min(page, Math.max(0,pages-1));
  function move(event, index) {
    const target = event.key === "Home" ? 0 : event.key === "End" ? points.length-1 : event.key === "ArrowLeft" ? index-1 : event.key === "ArrowRight" ? index+1 : null;
    if (target == null) return;
    event.preventDefault(); refs.current[Math.max(0,Math.min(points.length-1,target))]?.focus();
  }
  return <section className="rating-history-card" aria-labelledby={`${id}-heading`} style={{"--rating-line":COLORS[platform]||COLORS.codeforces}}>
    <header className="rating-history-header"><div><h3 id={`${id}-heading`}>{NAMES[platform]} Rating 走势</h3><span>仅 Rated · 最多最近 500 场</span></div>
      <div role="group" aria-label="Rating 历史时间范围">{[["all","全部"],["year","近一年"]].map(([value,label]) => <button type="button" key={value} aria-pressed={range===value} onClick={() => {setRange(value);setPage(0);}}>{label}</button>)}</div></header>
    {!model ? <p className="rating-history-empty" role="status">{all === null ? pending ? "Rating 历史正在后台同步。" : "Rating 历史暂不可用，请同步后重试。" : "所选时间内暂无 Rated 比赛记录。"}</p> : <>
      <dl className="rating-history-summary"><div><dt>最新记录</dt><dd>{model.latest.toLocaleString()}</dd></div><div><dt>区间最高</dt><dd>{model.peak.toLocaleString()}</dd></div><div><dt>图中比赛</dt><dd>{points.length}<small>场</small></dd></div></dl>
      <div className="rating-history-scroll" ref={canvasRef} tabIndex={0} role="region" aria-label="Rating 走势；可横向滚动，聚焦比赛后用左右方向键切换">
        <svg viewBox={`0 0 ${width} ${height}`} role="group" aria-label={`${NAMES[platform]} Rated 比赛 Rating 历史`} aria-describedby={`${id}-detail`}>
          <defs><linearGradient id={`${id}-fill`} x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor={COLORS[platform]} stopOpacity=".13"/><stop offset="100%" stopColor={COLORS[platform]} stopOpacity="0"/></linearGradient></defs>
          <g aria-hidden="true">
            <rect x={left} y={top} width={plotWidth} height={plotHeight} fill="var(--surface-subtle)" rx="8" />
            {platform === "codeforces" && CF_RATING_BANDS.map(([min,max,color]) => {
              const from = Math.max(model.min,min), to = Math.min(model.max,max);
              return to>from ? <rect key={min} x={left} y={y(to)} width={plotWidth} height={y(from)-y(to)} fill={color}/> : null;
            })}
            {model.ticks.map(tick => <g key={tick}><line className="rating-history-grid" x1={left} x2={width-right} y1={y(tick)} y2={y(tick)}/><text x={left-12} y={y(tick)+5} textAnchor="end">{tick}</text></g>)}
            {[0,1,2,3,4].map(index => {
              const second = model.start+(model.end-model.start)*index/4;
              const label = new Date(second*1000).toLocaleString("en-GB",{timeZone:"Asia/Shanghai",...(model.end-model.start>180*86400?{year:"numeric",month:"short"}:model.end-model.start<=2*86400?{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"}:{month:"short",day:"numeric"})});
              return <text key={index} x={left+plotWidth*index/4} y={height-20} textAnchor={index===0?"start":index===4?"end":"middle"}>{label}</text>;
            })}
            <path d={`${path} L ${x(points.at(-1).ratingUpdateTimeSeconds)} ${y(model.min)} L ${x(points[0].ratingUpdateTimeSeconds)} ${y(model.min)} Z`} fill={`url(#${id}-fill)`}/>
            <path d={path} fill="none" stroke="var(--rating-line)" strokeWidth="2.8" strokeLinejoin="round" />
            <line x1={x(selected.ratingUpdateTimeSeconds)} x2={x(selected.ratingUpdateTimeSeconds)} y1={top} y2={top+plotHeight} className="rating-history-guide" />
          </g>
          {points.map((point,index) => <g key={point.contestId} className="rating-history-point" ref={element => {refs.current[index]=element;}} role="button" tabIndex={selectedIndex===index?0:-1}
            aria-label={`${date(point.ratingUpdateTimeSeconds)}，${point.contestName}，Rating ${point.newRating}，变化 ${delta(point.ratingChange)}`} aria-pressed={selectedIndex===index}
            onPointerEnter={() => setChoice(point.contestId)} onClick={() => setChoice(point.contestId)} onFocus={() => setChoice(point.contestId)} onKeyDown={event => { move(event,index);if(["Enter"," "].includes(event.key)){event.preventDefault();setChoice(point.contestId);}}}>
            <circle cx={x(point.ratingUpdateTimeSeconds)} cy={y(point.newRating)} r="10" fill="transparent" />
            <circle className="rating-history-dot" cx={x(point.ratingUpdateTimeSeconds)} cy={y(point.newRating)} r={selectedIndex===index?6:3.5} fill="var(--paper)" stroke="var(--rating-line)" strokeWidth={selectedIndex===index?3:2}/>
          </g>)}
        </svg>
      </div>
      <div id={`${id}-detail`} className="rating-history-detail" aria-live="polite"><div><strong>{selected.contestName || `Contest ${selected.contestId}`}</strong><span>{date(selected.ratingUpdateTimeSeconds)} · Rank {selected.rank ?? "—"}</span></div>
        <p><b>{selected.newRating.toLocaleString()}</b><span data-positive={(selected.ratingChange??0)>0} data-negative={(selected.ratingChange??0)<0}>{delta(selected.ratingChange)}</span></p>
        {selected.url && <a href={selected.url} target="_blank" rel="noopener noreferrer">比赛页面<ExternalLink size={16} aria-hidden="true"/></a>}</div>
      <button type="button" className="rating-history-table-toggle" aria-expanded={tableOpen} onClick={() => setTableOpen(value=>!value)}>{tableOpen ? "收起比赛数据" : "查看比赛数据"}</button>
      {tableOpen && <div className="rating-history-table"><table><caption>比赛数据 · {safePage*20+1}–{Math.min(points.length,(safePage+1)*20)} / {points.length}</caption><thead><tr><th>日期</th><th>比赛</th><th>Rating</th><th>变化</th></tr></thead><tbody>{points.slice().reverse().slice(safePage*20,(safePage+1)*20).map(point => <tr key={point.contestId}><td>{date(point.ratingUpdateTimeSeconds)}</td><td>{point.contestName}</td><td>{point.newRating}</td><td>{delta(point.ratingChange)}</td></tr>)}</tbody></table>
        {pages>1 && <nav aria-label="比赛数据分页"><button type="button" disabled={!safePage} onClick={()=>setPage(value=>value-1)}>上一页</button><span>{safePage+1} / {pages}</span><button type="button" disabled={safePage>=pages-1} onClick={()=>setPage(value=>value+1)}>下一页</button></nav>}</div>}
    </>}
  </section>;
}
