import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import * as VisuallyHidden from "@radix-ui/react-visually-hidden";

export default function CheckInModal({ open, onOpenChange, onCheckIn, loading, result, title }) {
  const [submissionId, setSubmissionId] = useState("");
  const earnedScore = result?.earnedScore ?? result?.score ?? 0;

  function handleSubmit() {
    if (!submissionId.trim()) return;
    onCheckIn(Number(submissionId));
  }

  function handleClose() {
    setSubmissionId("");
    onOpenChange(false);
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleClose}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/30 z-40" />
        <Dialog.Content className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-white rounded-ui shadow-lg border border-border p-6 w-[400px] max-w-[90vw] z-50">
          <VisuallyHidden.Root>
            <Dialog.Title>{title || "提交打卡"}</Dialog.Title>
          </VisuallyHidden.Root>

          {!result ? (
            <div>
              <h3 className="text-base font-semibold text-text-primary m-0 mb-4">
                {title || "提交打卡"}
              </h3>
              <label className="block text-sm text-text-secondary mb-1.5">
                Codeforces 提交 ID
              </label>
              <input
                className="w-full px-3 py-2 text-sm border border-border rounded-ui bg-white text-text-primary outline-hidden focus:border-text-primary"
                value={submissionId}
                onChange={(e) => setSubmissionId(e.target.value)}
                placeholder="输入 submission ID"
                autoFocus
              />
              <div className="flex justify-end gap-2 mt-4">
                <button
                  className="px-3 py-1.5 text-nav border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary"
                  onClick={handleClose}
                >
                  取消
                </button>
                <button
                  className="px-3 py-1.5 text-nav rounded-ui bg-text-primary text-white hover:bg-[#1b1f23] disabled:opacity-50"
                  onClick={handleSubmit}
                  disabled={loading || !submissionId.trim()}
                >
                  {loading ? "排队校验中..." : "提交校验"}
                </button>
              </div>
              {loading && (
                <p className="m-0 mt-3 text-xs text-text-secondary">
                  Codeforces 官方接口有限流，任务会在后台按顺序完成，请保持页面打开。
                </p>
              )}
            </div>
          ) : (
            <div className="text-center">
              <div
                className={`text-lg font-semibold mb-2 ${
                  result.accepted ? "text-success" : "text-error"
                }`}
              >
                {result.accepted ? "校验通过" : "校验未通过"}
              </div>
              <p className="text-sm text-text-secondary m-0">
                Submission ID: {result.submissionId ?? "-"}
              </p>
              <p className="text-sm text-text-secondary m-0">
                Verdict: {result.verdict ?? "-"}
              </p>
              {result.accepted && earnedScore > 0 && (
                <p className="text-sm font-medium text-success m-0 mt-1">
                  +{earnedScore} 分
                </p>
              )}
              <button
                className="mt-4 px-4 py-1.5 text-nav rounded-ui bg-text-primary text-white hover:bg-[#1b1f23]"
                onClick={handleClose}
              >
                确定
              </button>
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
