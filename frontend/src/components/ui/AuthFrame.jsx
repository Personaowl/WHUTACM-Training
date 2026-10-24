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
