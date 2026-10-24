import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import AuthFrame from "../components/ui/AuthFrame";
import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage("");
    setLoading(true);
    try {
      const result = await login({
        username: account.trim(),
        password,
      });
      if (result.ok) {
        navigate("/");
      } else {
        setMessage(result.message);
      }
    } catch {
      setMessage("登录请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFrame
      title="欢迎回来"
      subtitle="使用你的训练平台账号继续。"
      footer={<>没有账号？<Link to="/register">创建账号</Link></>}
    >
      {message && <p role="alert" className="auth-message auth-message-error">{message}</p>}
      <form className="auth-form" onSubmit={handleSubmit}>
        <label>
          <span>登录账号</span>
          <input
            className="auth-field"
            value={account}
            onChange={(event) => setAccount(event.target.value)}
            placeholder="输入账号"
            autoComplete="username"
            required
          />
        </label>
        <label>
          <span>密码</span>
          <input
            className="auth-field"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="输入密码"
            autoComplete="current-password"
            required
          />
        </label>
        <button className="button-primary auth-submit" type="submit" disabled={loading}>
          {loading ? "登录中..." : "登录"}
        </button>
      </form>
    </AuthFrame>
  );
}
