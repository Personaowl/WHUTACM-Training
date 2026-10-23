import { MoveUpRight } from "lucide-react";
import { Link } from "react-router-dom";

export default function AuthFrame({ title, subtitle, children, footer }) {
  return (
    <main className="auth-shell">
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
          <ul className="auth-training-modes" aria-label="训练内容">
            <li><span>每日一题</span><small>每日选题 · 提交校验</small></li>
            <li><span>个人训练</span><small>自主练习 · 补题计划</small></li>
            <li><span>个人分析</span><small>训练记录 · 数据统计</small></li>
          </ul>
        </div>
        <p className="auth-platforms">Codeforces · AtCoder · 洛谷 · Nowcoder</p>
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
