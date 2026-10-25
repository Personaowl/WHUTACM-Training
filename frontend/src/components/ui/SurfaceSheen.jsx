import { useEffect, useRef, useState } from "react";

export default function SurfaceSheen() {
  const element = useRef(null);
  const [visible, setVisible] = useState(() => typeof document === "undefined" || document.visibilityState !== "hidden");
  useEffect(() => {
    let inViewport = true;
    const update = () => setVisible(inViewport && document.visibilityState !== "hidden");
    const observer = typeof IntersectionObserver === "undefined" ? null : new IntersectionObserver(entries => {
      inViewport = entries[0]?.isIntersecting ?? true;
      update();
    });
    if (element.current) observer?.observe(element.current.parentElement);
    document.addEventListener("visibilitychange", update);
    update();
    return () => { observer?.disconnect(); document.removeEventListener("visibilitychange", update); };
  }, []);
  return <span ref={element} className="surface-sheen" aria-hidden="true" style={{ animationPlayState: visible ? "running" : "paused" }} />;
}
