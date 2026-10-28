import { useEffect, useId, useMemo, useRef, useState } from "react";
import { ExternalLink } from "lucide-react";
import { normalizeRatingHistory, ratingHistoryModel } from "../../utils/ratingHistory";
import { ratingBand, ratingBands, ratingBandRange } from "../../utils/ratingPalette";
import "./RatingHistoryChart.css";

const NAMES = { codeforces: "Codeforces", atcoder: "AtCoder", nowcoder: "Nowcoder" };
const LINES = { codeforces: "#f7ca43", atcoder: "#637083", nowcoder: "#737d8d" };
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
  const bands = ratingBands(platform);
  const selectedBand = selected && ratingBand(platform, selected.newRating);
  const width = canvasWidth, height = 350, left = 66, right = platform === "nowcoder" ? 76 : 24, top = 24, bottom = 58;
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
  return <section className="rating-history-card" data-platform={platform} aria-labelledby={`${id}-heading`} style={{"--rating-line":LINES[platform]||LINES.codeforces}}>
    <header className="rating-history-header"><div><h3 id={`${id}-heading`}>{NAMES[platform]} Rating 走势</h3><span>仅 Rated · 最多最近 500 场</span></div>
      <div role="group" aria-label="Rating 历史时间范围">{[["all","全部"],["year","近一年"]].map(([value,label]) => <button type="button" key={value} aria-pressed={range===value} onClick={() => {setRange(value);setPage(0);}}>{label}</button>)}</div></header>
    {!model ? <p className="rating-history-empty" role="status">{all === null ? pending ? "Rating 历史正在后台同步。" : "Rating 历史暂不可用，请同步后重试。" : "所选时间内暂无 Rated 比赛记录。"}</p> : <>
      <dl className="rating-history-summary"><div><dt>最新记录</dt><dd style={{color:ratingBand(platform,model.latest)?.ink}}>{model.latest.toLocaleString()}</dd></div><div><dt>区间最高</dt><dd style={{color:ratingBand(platform,model.peak)?.ink}}>{model.peak.toLocaleString()}</dd></div><div><dt>图中比赛</dt><dd>{points.length}<small>场</small></dd></div></dl>
      <div className="rating-history-scroll" ref={canvasRef} tabIndex={0} role="region" aria-label="Rating 走势；可横向滚动，聚焦比赛后用左右方向键切换">
        <svg viewBox={`0 0 ${width} ${height}`} role="group" aria-label={`${NAMES[platform]} Rated 比赛 Rating 历史`} aria-describedby={`${id}-detail`}>
          <g aria-hidden="true">
            <rect x={left} y={top} width={plotWidth} height={plotHeight} fill="var(--paper)" />
            {platform !== "nowcoder" && bands.map(band => {
              const from = Math.max(model.min,band.min), to = Math.min(model.max,band.max);
              return to>from ? <rect className="rating-history-band" key={band.min} data-min={band.min} data-max={band.max} x={left} y={y(to)} width={plotWidth} height={y(from)-y(to)} fill={band.background}/> : null;
            })}
            {model.ticks.map(tick => <g key={tick}><line className="rating-history-grid" x1={left} x2={width-right} y1={y(tick)} y2={y(tick)}/><text x={left-12} y={y(tick)+5} textAnchor="end">{tick}</text></g>)}
            {bands.filter(band => band.min > model.min && band.min < model.max).map(band => <g key={band.min} className="rating-history-threshold" data-min={band.min}>
              <line x1={left} x2={width-right} y1={y(band.min)} y2={y(band.min)} stroke={platform === "nowcoder" ? band.color : "#ffffff"} />
              {platform === "nowcoder" && <text x={width-right+10} y={y(band.min)+5} style={{fill:band.ink}}>{band.min}</text>}
            </g>)}
            {[0,1,2,3,4].map(index => {
              const second = model.start+(model.end-model.start)*index/4;
              const label = new Date(second*1000).toLocaleString("en-GB",{timeZone:"Asia/Shanghai",...(model.end-model.start>180*86400?{year:"numeric",month:"short"}:model.end-model.start<=2*86400?{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"}:{month:"short",day:"numeric"})});
              return <g key={index}><line className="rating-history-vertical-grid" x1={left+plotWidth*index/4} x2={left+plotWidth*index/4} y1={top} y2={top+plotHeight}/><text x={left+plotWidth*index/4} y={height-20} textAnchor={index===0?"start":index===4?"end":"middle"}>{label}</text></g>;
            })}
            <rect className="rating-history-frame" x={left} y={top} width={plotWidth} height={plotHeight} fill="none"/>
            <path className="rating-history-line-halo" d={path} fill="none" strokeWidth="5.5" strokeLinejoin="round" />
            <path className="rating-history-line" d={path} fill="none" stroke="var(--rating-line)" strokeWidth="3" strokeLinejoin="round" />
            <line x1={x(selected.ratingUpdateTimeSeconds)} x2={x(selected.ratingUpdateTimeSeconds)} y1={top} y2={top+plotHeight} className="rating-history-guide" />
          </g>
          {points.map((point,index) => <g key={point.contestId} className="rating-history-point" ref={element => {refs.current[index]=element;}} role="button" tabIndex={selectedIndex===index?0:-1}
            aria-label={`${date(point.ratingUpdateTimeSeconds)}，${point.contestName}，Rating ${point.newRating}，${ratingBand(platform,point.newRating)?.label}分段，变化 ${delta(point.ratingChange)}`} aria-pressed={selectedIndex===index}
            onPointerEnter={() => setChoice(point.contestId)} onClick={() => setChoice(point.contestId)} onFocus={() => setChoice(point.contestId)} onKeyDown={event => { move(event,index);if(["Enter"," "].includes(event.key)){event.preventDefault();setChoice(point.contestId);}}}>
            <circle cx={x(point.ratingUpdateTimeSeconds)} cy={y(point.newRating)} r="12" fill="transparent" />
            {selectedIndex===index && <circle className="rating-history-selection" cx={x(point.ratingUpdateTimeSeconds)} cy={y(point.newRating)} r="10"/>}
            <circle className="rating-history-dot" data-rating={point.newRating} cx={x(point.ratingUpdateTimeSeconds)} cy={y(point.newRating)} r={selectedIndex===index?6:4.5} fill={platform === "codeforces" ? "#fffef6" : ratingBand(platform,point.newRating)?.color} stroke={platform === "codeforces" ? "#ad7200" : ratingBand(platform,point.newRating)?.ink} strokeWidth="1.5"/>
          </g>)}
        </svg>
      </div>
      <ul className="rating-history-legend" aria-label={`${NAMES[platform]} Rating 颜色分段`}>{bands.map(band => <li key={band.min}>
        <i aria-hidden="true" style={{background:band.background,borderColor:band.color}}/><span>{band.label}</span><strong>{ratingBandRange(band)}</strong>
      </li>)}</ul>
      <div id={`${id}-detail`} className="rating-history-detail" aria-live="polite"><div><strong>{selected.contestName || `Contest ${selected.contestId}`}</strong><span>{date(selected.ratingUpdateTimeSeconds)} · Rank {selected.rank ?? "—"}</span></div>
        <div className="rating-history-selected-score"><p><b style={{color:selectedBand.ink}}>{selected.newRating.toLocaleString()}</b><span data-positive={(selected.ratingChange??0)>0} data-negative={(selected.ratingChange??0)<0}>{delta(selected.ratingChange)}</span></p><small>{selectedBand.label} · {ratingBandRange(selectedBand)}</small></div>
        {selected.url && <a href={selected.url} target="_blank" rel="noopener noreferrer">比赛页面<ExternalLink size={16} aria-hidden="true"/></a>}</div>
      <button type="button" className="rating-history-table-toggle" aria-expanded={tableOpen} onClick={() => setTableOpen(value=>!value)}>{tableOpen ? "收起比赛数据" : "查看比赛数据"}</button>
      {tableOpen && <div className="rating-history-table"><table><caption>比赛数据 · {safePage*20+1}–{Math.min(points.length,(safePage+1)*20)} / {points.length}</caption><thead><tr><th>日期</th><th>比赛</th><th>Rating</th><th>变化</th></tr></thead><tbody>{points.slice().reverse().slice(safePage*20,(safePage+1)*20).map(point => <tr key={point.contestId}><td>{date(point.ratingUpdateTimeSeconds)}</td><td>{point.contestName}</td><td>{point.newRating}</td><td>{delta(point.ratingChange)}</td></tr>)}</tbody></table>
        {pages>1 && <nav aria-label="比赛数据分页"><button type="button" disabled={!safePage} onClick={()=>setPage(value=>value-1)}>上一页</button><span>{safePage+1} / {pages}</span><button type="button" disabled={safePage>=pages-1} onClick={()=>setPage(value=>value+1)}>下一页</button></nav>}</div>}
    </>}
  </section>;
}
