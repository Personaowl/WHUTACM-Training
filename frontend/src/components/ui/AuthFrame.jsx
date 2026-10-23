import { MoveUpRight } from "lucide-react";
import { Link } from "react-router-dom";

export default function AuthFrame({ title, subtitle, children, footer }) {
  return (
    <main className="auth-shell">
      <div className="auth-geometry" aria-hidden="true">
        <span className="geometry-ring geometry-ring-one" />
        <span className="geometry-ring geometry-ring-two" />
        <span className="geometry-square" />
        <span className="geometry-line" />
      </div>

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
        <div className="auth-seam-decoration" aria-hidden="true">
          <span className="auth-seam-node auth-seam-node-one" />
          <span className="auth-seam-node auth-seam-node-two" />
          <span className="auth-seam-node auth-seam-node-three" />
        </div>
      </section>

      <section className="auth-card-wrap">
        <div className="auth-card">
          <div className="auth-card-heading">
            <h1>{title}</h1>
            <p>{subtitle}</p>
          </div>
          {children}
          <div className="auth-card-footer">
            <span>{footer}</span>
            <MoveUpRight size={15} />
          </div>
        </div>
      </section>
    </main>
  );
}
