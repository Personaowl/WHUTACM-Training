import { useId } from "react";
import "./AmbientSky.css";

const STARS = [
  [24, 40, 1.5], [100, 70, 1], [48, 128, 2], [166, 158, 1.2],
  [26, 222, 1], [106, 258, 1.8], [68, 330, 1.2], [182, 372, 1],
  [32, 432, 2], [126, 476, 1], [76, 556, 1.5], [200, 590, 1],
  [36, 648, 1.2], [136, 716, 2], [68, 786, 1], [186, 854, 1.5],
];

const DUST_PATH = [
  [18, 24], [70, 48], [130, 30], [42, 76], [86, 104], [154, 92],
  [20, 144], [68, 170], [128, 208], [44, 240], [90, 220], [180, 260],
  [16, 294], [58, 278], [134, 312], [98, 352], [40, 360], [158, 404],
  [20, 466], [68, 448], [110, 422], [148, 490], [30, 526], [100, 548],
  [164, 574], [14, 606], [58, 620], [118, 596], [88, 674], [176, 650],
  [18, 702], [58, 734], [106, 762], [160, 746], [28, 814], [92, 846],
  [140, 818], [192, 794], [52, 874], [174, 888],
].map(([x, y]) => `M${x} ${y}h.01`).join(" ");

const GLINTS = {
  left: [[116, 112, 11], [64, 198, 13], [28, 388, 16], [102, 820, 13], [90, 670, 10], [26, 736, 11]],
  right: [[44, 86, 12], [86, 234, 11], [30, 458, 16], [112, 846, 12], [70, 650, 11], [42, 760, 10]],
};
const GLINT_PATHS = Object.fromEntries(Object.entries(GLINTS).map(([side, stars]) => [side,
  stars.map(([x, y, radius]) => {
    const neck = radius * .22;
    return `M${x} ${y - radius}l${neck} ${radius - neck} ${radius - neck} ${neck} ${neck - radius} ${neck} ${-neck} ${radius - neck} ${-neck} ${neck - radius} ${neck - radius} ${-neck} ${radius - neck} ${-neck}Z`;
  }).join(" "),
]));

function SkyEdge({ side }) {
  const glowId = `ambient-glow-${useId().replaceAll(":", "")}`;
  return <svg className={`ambient-sky-edge is-${side}`} viewBox="0 0 240 900" width="240" height="900"
    preserveAspectRatio="xMinYMid slice" fill="none" aria-hidden="true" focusable="false">
    <defs><radialGradient id={glowId}>
      <stop offset="0" stopColor="white" stopOpacity=".95" />
      <stop offset=".32" stopColor="white" stopOpacity=".55" />
      <stop offset=".65" stopColor="white" stopOpacity=".15" />
      <stop offset="1" stopColor="white" stopOpacity="0" />
    </radialGradient></defs>
    <g className="sky-orbits">
      <ellipse cx="-74" cy="636" rx="188" ry="152" transform="rotate(-24 -74 636)" />
      <ellipse cx="-74" cy="636" rx="212" ry="176" transform="rotate(-24 -74 636)" />
      <path d="M-32 180Q88 110 172 190M-20 198Q88 133 154 190" />
    </g>
    <g className="sky-constellation">
      <path d="m24 40 24 88 58 130-38 72M32 432l44 124 60 160-68 70" />
      <circle cx="48" cy="128" r="6" /><circle cx="136" cy="716" r="6" />
    </g>
    <g className="sky-stars">
      {STARS.map(([cx, cy, r]) => <circle key={cy} cx={cx} cy={cy} r={r * 1.5} />)}
    </g>
    <path className="sky-dust" d={DUST_PATH} transform={side === "right" ? "translate(6 -10)" : undefined} />
    <g className="sky-planet" transform={`translate(44 ${side === "left" ? 574 : 292}) rotate(-24)`}>
      <circle r="9" /><ellipse rx="21" ry="6" /><circle r="3" className="sky-planet-core" />
    </g>
    <g className="sky-glints">
      <g className="sky-glint-halos" fill={`url(#${glowId})`}>
        {GLINTS[side].map(([cx, cy, radius]) => <circle key={cy} className="sky-glint-halo" cx={cx} cy={cy} r={radius * 2.1} />)}
      </g>
      <path className="sky-glint-shape" d={GLINT_PATHS[side]} />
    </g>
  </svg>;
}

// One static backdrop per application frame; no timers, input handlers or external assets.
export default function AmbientSky() {
  return <div className="ambient-sky" aria-hidden="true">
    <SkyEdge side="left" /><SkyEdge side="right" />
  </div>;
}
