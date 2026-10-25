import "./SurfaceContours.css";

export default function SurfaceContours({ tone = "blue", wide = false }) {
  return (
    <svg className={`surface-contours${wide ? " is-wide" : ""}`} data-tone={tone}
      viewBox="0 0 240 160" width="240" height="160" preserveAspectRatio="xMaxYMid slice"
      fill="none" aria-hidden="true" focusable="false">
      <circle cx="240" cy="80" r="62" />
      <circle cx="240" cy="80" r="90" />
      <circle cx="240" cy="80" r="118" />
    </svg>
  );
}
