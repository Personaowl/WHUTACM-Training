import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Download, FileSpreadsheet, RefreshCw, X } from "lucide-react";
import { exportTrainingDashboard } from "../../api/admin";

const EXPORT_RANGES = [
  { value: "WEEK", label: "最近一周", description: "包含今天在内的最近 7 天" },
  { value: "MONTH", label: "最近一个月", description: "包含今天在内的最近 30 天" },
  { value: "ALL", label: "全部记录", description: "导出数据库中现有的全部历史" },
];

const EXPORT_TYPES = [
  { key: "includeDaily", label: "每日一题", description: "题目、日期、完成状态、得分与提交记录" },
  { key: "includeCodeforcesContests", label: "CF Rating 比赛", description: "比赛名次、Rating 变化与比赛链接" },
  { key: "includeAtCoderContests", label: "AtCoder ABC", description: "参赛状态、赛时 AC、名次、Performance 与豁免情况" },
];

export default function TrainingExportButton({ defaultType = "daily" }) {
  const [exportOpen, setExportOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState("");
  const [exportRange, setExportRange] = useState("WEEK");
  const [exportTypes, setExportTypes] = useState({
    includeDaily: defaultType === "daily",
    includeCodeforcesContests: false,
    includeAtCoderContests: defaultType === "weekly",
  });

  async function handleExport() {
    if (!Object.values(exportTypes).some(Boolean)) {
      setExportError("请至少选择一种训练数据");
      return;
    }
    setExporting(true);
    setExportError("");
    try {
      const { blob, filename } = await exportTrainingDashboard({
        range: exportRange,
        ...exportTypes,
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setExportOpen(false);
    } catch (requestError) {
      let message = "导出失败，请稍后重试";
      const errorBlob = requestError?.response?.data;
      if (errorBlob instanceof Blob) {
        try {
          const payload = JSON.parse(await errorBlob.text());
          if (payload?.message) message = payload.message;
        } catch {
          // 非 JSON 错误响应使用默认提示。
        }
      }
      setExportError(message);
    } finally {
      setExporting(false);
    }
  }

  return (
      <Dialog.Root open={exportOpen} onOpenChange={(open) => {
        if (exporting) return;
        setExportOpen(open);
        if (open) setExportError("");
      }}>
        <Dialog.Trigger asChild>
          <button
            type="button"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-white px-4 text-sm font-bold text-text-primary transition-colors hover:bg-bg-secondary"
          >
            <Download size={15} />
            导出 Excel
          </button>
        </Dialog.Trigger>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/45 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=closed]:animate-out" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-[81] max-h-[90vh] w-[calc(100vw-2rem)] max-w-2xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-5 shadow-[0_28px_90px_rgba(8,22,39,0.25)] sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-start gap-3">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#eaf1f8] text-[#122943]">
                  <FileSpreadsheet size={21} />
                </span>
                <div>
                  <Dialog.Title className="m-0 text-xl font-bold text-text-primary">导出现役队员训练明细</Dialog.Title>
                  <Dialog.Description className="m-0 mt-1 text-xs leading-5 text-text-secondary">
                    汇总表会始终生成，每个勾选的数据类型会写入独立工作表。
                  </Dialog.Description>
                </div>
              </div>
              <Dialog.Close asChild>
                <button type="button" className="rounded-lg p-2 text-text-secondary hover:bg-bg-secondary hover:text-text-primary" aria-label="关闭">
                  <X size={18} />
                </button>
              </Dialog.Close>
            </div>

            <fieldset className="mt-6 border-0 p-0">
              <legend className="mb-3 text-sm font-bold text-text-primary">时间范围</legend>
              <div className="grid gap-2 sm:grid-cols-3">
                {EXPORT_RANGES.map((item) => (
                  <label
                    key={item.value}
                    className={`cursor-pointer rounded-xl border p-3 transition-colors ${
                      exportRange === item.value
                        ? "border-[#122943] bg-[#eef4fb]"
                        : "border-border bg-white hover:bg-bg-secondary"
                    }`}
                  >
                    <span className="flex items-center gap-2 text-sm font-bold text-text-primary">
                      <input
                        type="radio"
                        name="export-range"
                        value={item.value}
                        checked={exportRange === item.value}
                        onChange={() => setExportRange(item.value)}
                        className="accent-[#122943]"
                      />
                      {item.label}
                    </span>
                    <small className="mt-1 block pl-5 text-[10px] leading-4 text-text-secondary">{item.description}</small>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className="mt-6 border-0 p-0">
              <div className="mb-3 flex items-center justify-between gap-3">
                <legend className="text-sm font-bold text-text-primary">训练数据</legend>
                <button
                  type="button"
                  className="text-xs font-bold text-[#245b8f] hover:underline"
                  onClick={() => {
                    const nextValue = !Object.values(exportTypes).every(Boolean);
                    setExportTypes({
                      includeDaily: nextValue,
                      includeCodeforcesContests: nextValue,
                      includeAtCoderContests: nextValue,
                    });
                    setExportError("");
                  }}
                >
                  {Object.values(exportTypes).every(Boolean) ? "取消全选" : "全部选择"}
                </button>
              </div>
              <div className="stack-y-2">
                {EXPORT_TYPES.map((item) => (
                  <label key={item.key} className="flex cursor-pointer items-start gap-3 rounded-xl border border-border p-3 hover:bg-bg-secondary">
                    <input
                      type="checkbox"
                      checked={exportTypes[item.key]}
                      onChange={(event) => {
                        setExportTypes((current) => ({ ...current, [item.key]: event.target.checked }));
                        setExportError("");
                      }}
                      className="mt-0.5 h-4 w-4 accent-[#122943]"
                    />
                    <span>
                      <strong className="block text-sm text-text-primary">{item.label}</strong>
                      <small className="mt-0.5 block text-[11px] leading-4 text-text-secondary">{item.description}</small>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <p className="m-0 mt-4 rounded-xl bg-bg-secondary px-3 py-2 text-[11px] leading-5 text-text-secondary">
              CF 比赛明细来自本地已同步的 Rating 历史；成员刷新 Codeforces 数据后会补全其历史记录。
            </p>
            {exportError && <p className="m-0 mt-3 text-sm font-semibold text-error">{exportError}</p>}

            <div className="mt-6 flex justify-end gap-2 border-t border-border pt-4">
              <Dialog.Close asChild>
                <button type="button" className="min-h-10 rounded-xl border border-border bg-white px-4 text-sm font-bold text-text-primary hover:bg-bg-secondary">
                  取消
                </button>
              </Dialog.Close>
              <button
                type="button"
                className="button-primary inline-flex min-w-32 items-center justify-center gap-2"
                disabled={exporting || !Object.values(exportTypes).some(Boolean)}
                onClick={handleExport}
              >
                {exporting ? <RefreshCw size={15} className="animate-spin" /> : <Download size={15} />}
                {exporting ? "生成中" : "生成并下载"}
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
  );
}
