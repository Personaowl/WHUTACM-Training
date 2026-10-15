import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import AuthFrame from "../components/ui/AuthFrame";
import { useAuth } from "../context/AuthContext";

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [account, setAccount] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [schoolInfo, setSchoolInfo] = useState({ realName: "", college: "", className: "" });
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage("");

    if (password !== confirmPassword) {
      setMessage("两次输入的密码不一致");
      return;
    }

    setLoading(true);
    try {
      const result = await register({
        username: account.trim(),
        displayName: displayName.trim(),
        ...Object.fromEntries(Object.entries(schoolInfo).map(([key, value]) => [key, value.trim()])),
        password,
      });
      if (result.ok) {
        setMessage("注册成功，请登录");
        setTimeout(() => navigate("/login"), 1000);
      } else {
        setMessage(result.message);
      }
    } catch {
      setMessage("注册请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFrame
      title="创建账号"
      subtitle="账号用于登录，用户名用于公开展示。"
      footer={<>已有账号？<Link to="/login">返回登录</Link></>}
    >
      {message && (
        <p className={`auth-message ${
          message.includes("成功") ? "auth-message-success" : "auth-message-error"
        }`}>
          {message}
        </p>
      )}
      <form className="auth-form" onSubmit={handleSubmit}>
        <label>
          <span>登录账号</span>
          <input
            className="auth-field"
            value={account}
            onChange={(event) => setAccount(event.target.value)}
            placeholder="用于登录的唯一账号"
            autoComplete="username"
            maxLength={50}
            required
          />
        </label>
        <label>
          <span>用户名</span>
          <input
            className="auth-field"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="对外展示的名称"
            maxLength={50}
            required
          />
        </label>
        <fieldset className="m-0 min-w-0 border border-border rounded-ui p-3 stack-y-3">
          <legend className="px-1 text-sm text-text-secondary">校内信息（选填）</legend>
          {[
            { name: "realName", label: "姓名", placeholder: "填写真实姓名", maxLength: 100 },
            { name: "college", label: "学院", placeholder: "填写所在学院", maxLength: 150 },
            { name: "className", label: "班级", placeholder: "填写所在班级", maxLength: 150 },
          ].map(({ name, label, ...attributes }) => (
            <label key={name}>
              <span>{label}</span>
              <input {...attributes} name={name} className="auth-field" value={schoolInfo[name]}
                onChange={(event) => setSchoolInfo((current) => ({ ...current, [name]: event.target.value }))} />
            </label>
          ))}
        </fieldset>
        <label>
          <span>密码</span>
          <input
            className="auth-field"
            type="password"
            minLength={8}
            maxLength={64}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="至少 8 位"
            autoComplete="new-password"
            required
          />
        </label>
        <label>
          <span>确认密码</span>
          <input
            className="auth-field"
            type="password"
            value={confirmPassword}
            minLength={8}
            maxLength={64}
            onChange={(event) => setConfirmPassword(event.target.value)}
            placeholder="再次输入密码"
            autoComplete="new-password"
            required
          />
        </label>
        <button className="button-primary auth-submit" type="submit" disabled={loading}>
          {loading ? "注册中..." : "创建账号"}
        </button>
      </form>
    </AuthFrame>
  );
}
