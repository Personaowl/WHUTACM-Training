import { useState, useEffect } from "react";
import { getDevTime, setDevTime, resetDevTime, forceDevCheckIn } from "../../api/dev";
import { useAuth } from "../../context/AuthContext";

/** 开发调试面板——由 AppLayout 在确认后端为 dev profile 后才渲染 */
export default function DevPanel() {
  const { user } = useAuth();
  const [devTime, setDevTimeState] = useState(null);
  const [inputDateTime, setInputDateTime] = useState("");
  const [checkInDate, setCheckInDate] = useState(getTodayStr());
  const [checkInSlot, setCheckInSlot] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    loadDevTime();
  }, []);

  async function loadDevTime() {
    try {
      const resp = await getDevTime();
      if (resp.code === 200) {
        setDevTimeState(resp.data);
        if (resp.data.fixed) {
          setInputDateTime(resp.data.dateTime || "");
        } else {
          setInputDateTime(getTodayStr() + "T00:00:00");
        }
      }
    } catch {
      // dev endpoints unavailable in prod
    }
  }

  async function handleSetTime() {
    if (!inputDateTime.trim()) return;
    try {
      const resp = await setDevTime(inputDateTime.trim());
      if (resp.code === 200) {
        setDevTimeState(resp.data);
        setMessage("时间已设置: " + resp.data.today);
      } else {
        setMessage(resp.message || "设置失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleResetTime() {
    try {
      const resp = await resetDevTime();
      if (resp.code === 200) {
        setDevTimeState(resp.data);
        setInputDateTime(getTodayStr() + "T00:00:00");
        setMessage("时间已重置为系统时间");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleForceCheckIn() {
    try {
      const resp = await forceDevCheckIn(checkInDate, checkInSlot || null, user?.id);
      if (resp.code === 200) {
        setMessage(
          `强制打卡成功: ${resp.data.date} / ${resp.data.slot} / score=${resp.data.score}`
        );
      } else {
        setMessage(resp.message || "打卡失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  return (
    <div className="bg-[#fff8f0] border border-warning/30 rounded-ui p-4 stack-y-4">
      <div className="flex items-center gap-2">
        <span className="text-xs font-semibold text-warning uppercase tracking-wide">DEV TOOLS</span>
        {devTime?.fixed && (
          <span className="text-xs bg-warning/10 text-warning px-1.5 py-0.5 rounded-sm">
            模拟时间: {devTime.today}
          </span>
        )}
      </div>

      {message && (
        <p className="text-xs text-text-secondary m-0">{message}</p>
      )}

      {/* Time setter */}
      <div>
        <p className="text-xs font-medium text-text-primary m-0 mb-1.5">⏰ 模拟时间</p>
        <div className="flex gap-2 items-center">
          <input
            type="datetime-local"
            className="flex-1 px-2 py-1 text-xs border border-border rounded-ui outline-hidden"
            value={inputDateTime}
            onChange={(e) => setInputDateTime(e.target.value)}
          />
          <button
            className="px-2.5 py-1 text-xs font-medium text-white bg-warning hover:bg-[#b08816] rounded-sm border-0 cursor-pointer shrink-0"
            onClick={handleSetTime}
          >
            设置
          </button>
          <button
            className="px-2.5 py-1 text-xs border border-border rounded-sm bg-white text-text-secondary hover:bg-bg-secondary cursor-pointer shrink-0"
            onClick={handleResetTime}
          >
            重置
          </button>
        </div>
      </div>

      {/* Force check-in */}
      <div>
        <p className="text-xs font-medium text-text-primary m-0 mb-1.5">✅ 强制打卡</p>
        <div className="flex gap-2 items-center">
          <input
            type="date"
            className="px-2 py-1 text-xs border border-border rounded-ui outline-hidden"
            value={checkInDate}
            onChange={(e) => setCheckInDate(e.target.value)}
          />
          <select
            className="px-2 py-1 text-xs border border-border rounded-ui outline-hidden bg-white"
            value={checkInSlot}
            onChange={(e) => setCheckInSlot(e.target.value)}
          >
            <option value="">自动选择</option>
            <option value="easy">easy</option>
            <option value="hard">hard</option>
          </select>
          <button
            className="px-2.5 py-1 text-xs font-medium text-white bg-success hover:bg-[#268845] rounded-sm border-0 cursor-pointer shrink-0"
            onClick={handleForceCheckIn}
          >
            完成打卡
          </button>
        </div>
      </div>
    </div>
  );
}

function getTodayStr() {
  return new Date().toISOString().slice(0, 10);
}
