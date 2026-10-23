import { useEffect, useState } from "react";
import { CheckCircle2, ExternalLink } from "lucide-react";
import http from "../../api/http";
import { authHeaders } from "../../api/auth";

export default function AdditionalPlatformBinding({ userId, platform, label, onConnected }) {
  const [binding, setBinding] = useState(null);
  const [uid, setUid] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [success, setSuccess] = useState(false);
  const base = `/api/users/${userId}/platform-bindings/${platform}`;
  useEffect(() => {
    if (!userId) return;
    let active = true;
    http.get(base, { headers: authHeaders() }).then(({ data }) => {
      if (!active) return;
      if (data.code !== 200) throw new Error(data.message);
      setBinding(data.data);
      setUid(data.data.pendingAccountId || data.data.accountId || "");
      onConnected(platform, Boolean(data.data.accountId));
    }).catch(error => { if (active) setMessage(error.message || "绑定信息加载失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [base]);
  const active = Boolean(binding?.verificationToken && binding?.pendingAccountId === uid);
  async function submit() {
    if (!/^[1-9][0-9]{0,17}$/.test(uid)) {
      setSuccess(false); setMessage("请输入个人主页地址中的数字 UID，而非昵称"); return;
    }
    setBusy(true); setMessage(""); setSuccess(false);
    try {
      const { data } = await http.post(base + (active ? "/finish" : "/start"),
        active ? {} : { uid }, { headers: authHeaders() });
      if (data.code !== 200) throw new Error(data.message);
      setBinding(data.data);
      onConnected(platform, Boolean(data.data.accountId));
      setSuccess(true);
      setMessage(active ? "所有权验证成功，可恢复原个人介绍" : "请将下方验证码临时加入个人介绍并保存");
    } catch (error) { setMessage(error.message || "验证失败，请稍后重试"); }
    finally { setBusy(false); }
  }
  return (
    <section className="binding-card" style={{ "--binding-accent": platform === "luogu" ? "#259e89" : "#31849c" }}>
      <div className="binding-card-head">
        <div className="binding-platform-mark" aria-hidden="true"><strong>{label[0]}</strong></div>
        <div><h2>{label}</h2>
          <span className={binding?.accountId ? "is-connected" : ""}>
            {binding?.accountId ? <><CheckCircle2 size={14} /> 已验证 UID {binding.accountId}</> : "尚未绑定"}
          </span>
        </div>
      </div>
      <p className="binding-card-description">通过公开个人介绍验证账号所有权，为后续训练接入保留账号关联；当前不自动统计该平台训练数据。</p>
      <p className="text-sm text-text-secondary">UID 是主页地址末尾的数字：{platform === "luogu" ? "luogu.com.cn/user/123" : "nowcoder.com/users/123（竞赛主页 profile/123 也是同一 UID）"}。</p>
      <div className="binding-input-row">
        <input aria-label={label + " UID"} className="form-input" inputMode="numeric" maxLength={18}
          value={uid} onChange={event => { setUid(event.target.value.trim()); setSuccess(false); setMessage(""); }}
          placeholder={label + "数字 UID"} disabled={busy || loading} />
        <button type="button" className="button-primary" onClick={submit} disabled={busy || loading}>
          {busy ? "处理中..." : loading ? "加载中..." : active ? "我已保存，完成验证" : "开始验证"}
        </button>
      </div>
      {active && <div className="binding-step">
        <div className="binding-step-title">验证账号所有权</div>
        <div className="binding-step-copy">
          前往 <a href={binding.profileUrl} target="_blank" rel="noreferrer">{label}个人主页 <ExternalLink size={13} /></a>，
          编辑{platform === "luogu" ? "个人介绍" : "个人简介"}，临时加入下面的完整验证码并保存：
          <code style={{ overflowWrap: "anywhere" }}>{binding.verificationToken}</code>
          验证成功后可删除验证码。不要把验证码发到讨论、评论或题解中。
        </div>
        <small>截止时间：{new Date(binding.expiresAtSeconds * 1000).toLocaleTimeString()}</small>
        <button type="button" className="home-text-link" disabled={busy} onClick={() => setBinding(current => ({ ...current, verificationToken: null }))}>重新发起验证</button>
      </div>}
      {message && <div role="status" className={`notice ${success ? "notice-success" : "notice-error"}`}>{message}</div>}
    </section>
  );
}
