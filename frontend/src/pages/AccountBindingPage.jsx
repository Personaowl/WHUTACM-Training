import { useEffect, useState } from "react";
import {
  CheckCircle2,
  Clock3,
  ExternalLink,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import {
  finishAtCoderBinding,
  finishCodeforcesBinding,
  getUserById,
  startAtCoderBinding,
  startCodeforcesBinding,
} from "../api/user";
import { CardSkeleton } from "../components/ui/Skeleton";

export default function AccountBindingPage() {
  const { user, updateUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState("");
  const [cfHandle, setCfHandle] = useState("");
  const [cfActive, setCfActive] = useState(false);
  const [cfExpiresAt, setCfExpiresAt] = useState(null);
  const [cfLoading, setCfLoading] = useState(false);
  const [cfMessage, setCfMessage] = useState("");
  const [atCoderHandle, setAtCoderHandle] = useState("");
  const [atCoderActive, setAtCoderActive] = useState(false);
  const [atCoderToken, setAtCoderToken] = useState("");
  const [atCoderExpiresAt, setAtCoderExpiresAt] = useState(null);
  const [atCoderLoading, setAtCoderLoading] = useState(false);
  const [atCoderMessage, setAtCoderMessage] = useState("");

  useEffect(() => {
    if (!user?.id) return undefined;
    let cancelled = false;

    getUserById(user.id)
      .then((response) => {
        if (cancelled) return;
        if (response?.code !== 200) throw new Error(response?.message || "账号信息加载失败");
        const nextProfile = response.data;
        setProfile(nextProfile);
        setCfHandle(nextProfile?.pendingCodeforcesHandle || nextProfile?.codeforcesHandle || "");
        if (nextProfile?.pendingCodeforcesHandle && nextProfile?.codeforcesBindingStartedAtSeconds) {
          setCfActive(true);
          setCfExpiresAt(nextProfile.codeforcesBindingStartedAtSeconds + 120);
        }
        setAtCoderHandle(nextProfile?.pendingAtcoderHandle || nextProfile?.atcoderHandle || "");
        if (nextProfile?.pendingAtcoderHandle && nextProfile?.atcoderBindingStartedAtSeconds) {
          setAtCoderActive(true);
          setAtCoderToken(nextProfile?.atcoderBindingToken || "");
          setAtCoderExpiresAt(nextProfile.atcoderBindingStartedAtSeconds + 600);
        }
      })
      .catch((error) => {
        if (!cancelled) setPageError(error.message || "账号信息加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [user?.id]);

  async function startCfBinding() {
    const handle = cfHandle.trim();
    if (!handle) {
      setCfMessage("请输入 Codeforces Handle");
      return;
    }
    setCfLoading(true);
    setCfMessage("");
    try {
      const response = await startCodeforcesBinding(user.id, handle);
      if (response?.code === 200) {
        setCfActive(true);
        setCfExpiresAt(response.data.expiresAtSeconds);
        setCfMessage("验证已开始，请在 2 分钟内完成 CE 提交");
      } else {
        setCfMessage(response?.message || "无法开始验证");
      }
    } catch {
      setCfMessage("绑定请求失败");
    } finally {
      setCfLoading(false);
    }
  }

  async function finishCfBinding() {
    setCfLoading(true);
    setCfMessage("");
    try {
      const response = await finishCodeforcesBinding(user.id);
      if (response?.code === 200) {
        setProfile(response.data);
        updateUser(response.data);
        setCfHandle(response.data.codeforcesHandle || "");
        setCfActive(false);
        setCfExpiresAt(null);
        setCfMessage("Codeforces 账号绑定成功");
      } else {
        if (response?.message?.includes("expired")) {
          setCfActive(false);
          setCfExpiresAt(null);
        }
        setCfMessage(response?.message || "未检测到验证提交");
      }
    } catch {
      setCfMessage("验证请求失败");
    } finally {
      setCfLoading(false);
    }
  }

  async function startAtCoderAccountBinding() {
    const handle = atCoderHandle.trim();
    if (!handle) {
      setAtCoderMessage("请输入 AtCoder Handle");
      return;
    }
    setAtCoderLoading(true);
    setAtCoderMessage("");
    try {
      const response = await startAtCoderBinding(user.id, handle);
      if (response?.code === 200) {
        setAtCoderActive(true);
        setAtCoderToken(response.data.verificationToken);
        setAtCoderExpiresAt(response.data.expiresAtSeconds);
        setAtCoderMessage("验证已开始，请将验证码临时写入 AtCoder Affiliation");
      } else {
        setAtCoderMessage(response?.message || "无法开始 AtCoder 验证");
      }
    } catch {
      setAtCoderMessage("AtCoder 绑定请求失败");
    } finally {
      setAtCoderLoading(false);
    }
  }

  async function finishAtCoderAccountBinding() {
    setAtCoderLoading(true);
    setAtCoderMessage("");
    try {
      const response = await finishAtCoderBinding(user.id);
      if (response?.code === 200) {
        setProfile(response.data);
        updateUser(response.data);
        setAtCoderHandle(response.data.atcoderHandle || "");
        setAtCoderActive(false);
        setAtCoderToken("");
        setAtCoderExpiresAt(null);
        setAtCoderMessage("AtCoder 账号绑定成功，现在可以恢复原 Affiliation");
      } else {
        if (response?.message?.includes("过期")) setAtCoderActive(false);
        setAtCoderMessage(response?.message || "未检测到 Affiliation 验证码");
      }
    } catch {
      setAtCoderMessage("AtCoder 验证请求失败");
    } finally {
      setAtCoderLoading(false);
    }
  }

  if (loading) {
    return <div className="stack-y-4"><h1 className="page-title">账号绑定</h1><CardSkeleton /><CardSkeleton /></div>;
  }

  return (
    <div className="stack-y-5">
      <header className="page-heading">
        <div>
          <h1 className="page-title">账号绑定</h1>
          <p>集中管理竞赛平台身份，绑定成功后训练数据会自动归档。</p>
        </div>
        <div className="binding-summary" aria-label="绑定状态">
          <ShieldCheck size={19} />
          <span>{Number(Boolean(profile?.codeforcesHandle)) + Number(Boolean(profile?.atcoderHandle))}/2 已连接</span>
        </div>
      </header>

      {pageError && <div className="notice notice-error">{pageError}</div>}

      <div className="grid gap-4 xl:grid-cols-2">
        <BindingCard
          platform="Codeforces"
          accent="#2f6fb2"
          iconSrc="/platforms/codeforces.png"
          connectedHandle={profile?.codeforcesHandle}
          message={cfMessage}
          success={cfMessage.includes("成功") || cfMessage.includes("已开始")}
        >
          <p className="binding-card-description">用于校验每日一题提交，并同步 Rating、比赛和做题数据。</p>
          <BindingInput
            value={cfHandle}
            onChange={setCfHandle}
            placeholder="Codeforces Handle"
            disabled={cfLoading || cfActive}
            active={cfActive}
            loading={cfLoading}
            onStart={startCfBinding}
            onFinish={finishCfBinding}
            finishText="我已提交，完成绑定"
          />
          {cfActive && (
            <BindingStep expiresAt={cfExpiresAt}>
              使用 <strong>{cfHandle}</strong> 向{" "}
              <a href="https://codeforces.com/contest/1/problem/A" target="_blank" rel="noreferrer">Codeforces 1A <ExternalLink size={13} /></a>
              提交一份会产生 Compilation Error 的代码。
            </BindingStep>
          )}
        </BindingCard>

        <BindingCard
          platform="AtCoder"
          accent="#e8793e"
          iconSrc="/platforms/atcoder.png"
          connectedHandle={profile?.atcoderHandle}
          message={atCoderMessage}
          success={atCoderMessage.includes("成功") || atCoderMessage.includes("已开始")}
        >
          <p className="binding-card-description">用于统计现役队员每周 ABC 的参赛状态与 AC 完成情况。</p>
          <BindingInput
            value={atCoderHandle}
            onChange={setAtCoderHandle}
            placeholder="AtCoder Handle"
            disabled={atCoderLoading || atCoderActive}
            active={atCoderActive}
            loading={atCoderLoading}
            onStart={startAtCoderAccountBinding}
            onFinish={finishAtCoderAccountBinding}
            finishText="我已修改，完成绑定"
          />
          {atCoderActive && (
            <BindingStep expiresAt={atCoderExpiresAt}>
              前往 <a href="https://atcoder.jp/settings" target="_blank" rel="noreferrer">AtCoder Settings <ExternalLink size={13} /></a>，
              临时把 Affiliation 设置为：
              <code>{atCoderToken || "读取验证码中"}</code>
              验证成功后即可恢复原内容。
            </BindingStep>
          )}
        </BindingCard>
      </div>
    </div>
  );
}

function BindingCard({ platform, accent, iconSrc, connectedHandle, message, success, children }) {
  return (
    <section className="binding-card" style={{ "--binding-accent": accent }}>
      <div className="binding-card-head">
        <div className="binding-platform-mark">
          <img src={iconSrc} alt={`${platform} 官方图标`} />
        </div>
        <div>
          <h2>{platform}</h2>
          <span className={connectedHandle ? "is-connected" : ""}>
            {connectedHandle ? <><CheckCircle2 size={14} /> 已连接 @{connectedHandle}</> : "尚未连接"}
          </span>
        </div>
      </div>
      {children}
      {message && <div className={`notice ${success ? "notice-success" : "notice-error"}`}>{message}</div>}
    </section>
  );
}

function BindingInput({ value, onChange, placeholder, disabled, active, loading, onStart, onFinish, finishText }) {
  return (
    <div className="binding-input-row">
      <input className="form-input" value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} disabled={disabled} />
      <button type="button" className="button-primary" onClick={active ? onFinish : onStart} disabled={loading}>
        {loading ? "检查中..." : active ? finishText : "开始验证"}
      </button>
    </div>
  );
}

function BindingStep({ expiresAt, children }) {
  return (
    <div className="binding-step">
      <div className="binding-step-title"><Clock3 size={16} />完成平台验证</div>
      <div className="binding-step-copy">{children}</div>
      {expiresAt && <small>截止时间：{new Date(expiresAt * 1000).toLocaleTimeString()}</small>}
    </div>
  );
}
