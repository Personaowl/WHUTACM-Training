import { ArrowUpRight, ShieldCheck } from "lucide-react";
import { PageIcon } from "./PageHeading";
import SurfaceSheen from "./SurfaceSheen";
import { Link } from "react-router-dom";
import "./AuthFrame.css";

export default function AuthFrame({ title, subtitle, children, footer }) {
  return (
    <main className="auth-shell auth-shell-bridge">
      <section className="auth-intro">
        <Link to="/" className="brand-lockup">
          <span className="brand-mark">
            <img src="/whut-acm-logo.png" alt="" aria-hidden="true" />
          </span>
          <span>WHUT-ACM</span>
        </Link>
        <div className="auth-intro-copy">
          <h2>WHUT-ACM<br />训练平台</h2>
          <p className="auth-intro-affiliation">武汉理工大学ACM协会</p>
        </div>
        <div className="auth-cover-decoration" aria-hidden="true"><span /><span /><i /></div>
        <SurfaceSheen />
      </section>

      <section className="auth-card-wrap">
        <svg className="auth-background-lines" viewBox="0 0 640 380" width="640" height="380" fill="none" aria-hidden="true" focusable="false">
          <g className="auth-contour-blue">
            <path d="M-40 310H150C252 310 222 174 324 174H410C508 174 478 38 576 38H680" />
            <path d="M-40 342H174C276 342 246 206 348 206H434C532 206 502 70 600 70H680" />
            <path d="M-40 374H198C300 374 270 238 372 238H458C556 238 526 102 624 102H680" />
          </g>
          <path className="auth-contour-mint" d="M52 406H222C324 406 294 270 396 270H482C580 270 550 134 648 134H680" />
          <path className="auth-contour-violet" d="M-40 278H126C228 278 198 142 300 142H386C484 142 454 6 552 6H680" />
          <g className="auth-contour-nodes">
            <circle cx="324" cy="174" r="5" />
            <circle cx="434" cy="206" r="5" />
            <circle cx="576" cy="38" r="5" />
          </g>
        </svg>
        <div className="auth-card">
          <div className="auth-card-heading">
            <div className="auth-title-row"><PageIcon icon={ShieldCheck} /><h1>{title}</h1></div>
            <p>{subtitle}</p>
          </div>
          {children}
          <div className="auth-card-footer">
            <span>{footer}</span>
            <ArrowUpRight size={18} aria-hidden="true" />
          </div>
        </div>
      </section>
    </main>
  );
}
