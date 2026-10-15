import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { MessageSquareText, X } from "lucide-react";
import { submitWeeklyExplanation } from "../../api/weeklyTraining";

export default function WeeklyExplanationDialog({ week, member, onUpdated }) {
  const [open, setOpen] = useState(false);
  const [content, setContent] = useState("");
  const [version, setVersion] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  function changeOpen(next) {
    if (saving) return;
    if (next) {
      setContent(member.explanation.content || "");
      setVersion(member.version);
      setError("");
    }
    setOpen(next);
  }

  async function submit(event) {
    event.preventDefault();
    if (saving || !content.trim()) return;
    setSaving(true);
    setError("");
    try {
      const response = await submitWeeklyExplanation(week, {
        content: content.trim(),
        version,
      });
      if (response.code !== 200)
        throw new Error(response.message || "说明提交失败");
      onUpdated(response.data);
      setOpen(false);
    } catch (failure) {
      setError(failure.message || "说明提交失败，请检查网络后重试");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={changeOpen}>
      <Dialog.Trigger asChild>
        <button
          type="button"
          className="button-primary inline-flex items-center gap-2"
        >
          <MessageSquareText size={17} />
          {member.explanation.content ? "更新情况说明" : "情况说明"}
        </button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/45" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-[81] max-h-[90dvh] w-[calc(100vw-2rem)] max-w-xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-5 shadow-xl sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <Dialog.Title className="m-0 text-xl font-bold text-text-primary">
              周训练情况说明
            </Dialog.Title>
            <Dialog.Close
              disabled={saving}
              aria-label="关闭情况说明"
              className="rounded-xl p-2 text-text-secondary hover:bg-bg-secondary"
            >
              <X size={18} />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mb-4 mt-2 text-sm leading-6 text-text-secondary">
            对应 {week}{" "}
            开始的一周。说明提交后由管理员审核，不会直接修改原始训练数据或积分。
          </Dialog.Description>
          <form onSubmit={submit} className="space-y-4">
            <label className="block text-sm font-semibold text-text-primary">
              说明内容
              <textarea
                autoFocus
                required
                maxLength={2000}
                rows={7}
                disabled={saving}
                value={content}
                onChange={(event) => setContent(event.target.value)}
                placeholder="请说明本周未能按要求训练的原因、涉及日期等信息。"
                className="mt-2 box-border w-full resize-y rounded-xl border border-border bg-bg-secondary p-3 text-sm leading-6 outline-none focus:border-[#245b8f]"
              />
            </label>
            <div className="flex justify-between gap-3 text-xs text-text-secondary">
              <span>
                更新说明会重新进入待审核；已有人工周状态保留至管理员再次调整。
              </span>
              <span className="shrink-0">{content.length}/2000</span>
            </div>
            {error && (
              <p role="alert" className="m-0 text-sm text-error">
                {error}
              </p>
            )}
            <div className="flex justify-end gap-2 border-t border-border pt-4">
              <Dialog.Close
                disabled={saving}
                className="min-h-10 rounded-xl border border-border bg-white px-4 text-sm font-bold text-text-primary"
              >
                取消
              </Dialog.Close>
              <button
                type="submit"
                disabled={saving || !content.trim()}
                className="button-primary"
              >
                {saving ? "提交中…" : "提交说明"}
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
