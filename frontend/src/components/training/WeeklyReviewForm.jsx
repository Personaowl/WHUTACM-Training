import { useState } from "react";
import { reviewWeeklyTraining } from "../../api/weeklyTraining";

export default function WeeklyReviewForm({
  week,
  member,
  onUpdated,
  onSavingChange,
  onReload,
}) {
  const pending = member.explanation.reviewStatus === "PENDING";
  const [action, setAction] = useState(pending ? "APPROVE" : "ADJUST");
  const [status, setStatus] = useState(
    pending ? "EXEMPT" : member.manualStatus,
  );
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [version] = useState(member.version);

  async function submit(event) {
    event.preventDefault();
    if (saving || !note.trim()) return;
    setSaving(true);
    onSavingChange(true);
    setError("");
    try {
      const response = await reviewWeeklyTraining(week, member.userId, {
        action,
        status,
        note: note.trim(),
        version,
      });
      if (response.code !== 200)
        throw new Error(response.message || "保存失败");
      onUpdated(response.data);
    } catch (failure) {
      setError(failure.message || "保存失败，请检查网络后重试");
    } finally {
      setSaving(false);
      onSavingChange(false);
    }
  }

  return (
    <form
      onSubmit={submit}
      className="mt-5 space-y-4 rounded-xl border border-[#d4dfeb] bg-[#f7f9fc] p-4"
    >
      <h3 className="m-0 text-base font-bold text-text-primary">
        {pending ? "审核情况说明" : "调整本周状态"}
      </h3>
      <fieldset
        disabled={saving}
        className="m-0 min-w-0 space-y-4 border-0 p-0"
      >
        {pending && (
          <div className="flex flex-wrap gap-4 text-sm text-text-primary">
            <label className="inline-flex items-center gap-2">
              <input
                type="radio"
                name="weekly-review-action"
                checked={action === "APPROVE"}
                onChange={() => setAction("APPROVE")}
              />
              同意说明并设置状态
            </label>
            <label className="inline-flex items-center gap-2">
              <input
                type="radio"
                name="weekly-review-action"
                checked={action === "REJECT"}
                onChange={() => setAction("REJECT")}
              />
              不同意说明
            </label>
          </div>
        )}
        {action !== "REJECT" && (
          <label className="flex flex-wrap items-center gap-3 text-sm font-semibold text-text-primary">
            本周最终状态
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              className="min-h-10 rounded-xl border border-border bg-white px-3 text-sm"
            >
              <option value="EXEMPT">已豁免</option>
              <option value="COMPLETED">已达标</option>
              <option value="INCOMPLETE">未达标</option>
              <option value="AUTO">恢复自动判定</option>
            </select>
          </label>
        )}
        <label className="block text-sm font-semibold text-text-primary">
          处理意见 / 调整原因
          <textarea
            required
            maxLength={2000}
            rows={3}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className="mt-2 box-border w-full rounded-xl border border-border bg-white p-3 text-sm outline-none focus:border-[#245b8f]"
            placeholder="必填，选手可看到此意见。"
          />
        </label>
      </fieldset>
      <p className="m-0 text-xs leading-5 text-text-secondary">
        只影响该成员本周的最终状态，不会改变每日题、比赛原始记录或积分。不同意说明时保留此前人工状态；如需撤销，请随后恢复自动判定。
      </p>
      {error && (
        <div role="alert" className="text-sm text-error">
          {error}
          <button
            type="button"
            disabled={saving}
            onClick={onReload}
            className="ml-2 underline"
          >
            刷新记录
          </button>
        </div>
      )}
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={saving || !note.trim()}
          className="button-primary"
        >
          {saving ? "保存中…" : pending ? "确认处理" : "保存状态"}
        </button>
      </div>
    </form>
  );
}
