import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import useWeeklyTraining from "../../hooks/useWeeklyTraining";
import WeekPicker from "../../components/training/WeekPicker";
import WeeklyTrainingDetails, {
  TrainingStatus,
} from "../../components/training/WeeklyTrainingDetails";
import WeeklyReviewForm from "../../components/training/WeeklyReviewForm";
import UserAvatar from "../../components/ui/UserAvatar";
import EmptyState from "../../components/ui/EmptyState";
import { REVIEW_LABELS } from "../../utils/trainingWeek";

export default function AdminWeeklyRecordsPage() {
  const { report, loading, error, setWeek, reload, updateReport } =
    useWeeklyTraining(true);
  const [selectedId, setSelectedId] = useState(null);
  const [filter, setFilter] = useState("all");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const members = report?.members || [];
  const selected = members.find((member) => member.userId === selectedId);
  const visible = members.filter(
    (member) =>
      filter === "all" ||
      (filter === "pending"
        ? member.explanation.reviewStatus === "PENDING"
        : member.effectiveStatus === filter),
  );
  const statistics = [
    ["训练成员", members.length],
    [
      "已达标 / 豁免",
      members.filter((member) =>
        ["COMPLETED", "EXEMPT"].includes(member.effectiveStatus),
      ).length,
    ],
    [
      "待审核说明",
      members.filter((member) => member.explanation.reviewStatus === "PENDING")
        .length,
    ],
    [
      "未达标",
      members.filter((member) => member.effectiveStatus === "INCOMPLETE")
        .length,
    ],
  ];

  function changeWeek(week) {
    if (saving) return;
    setSelectedId(null);
    setNotice("");
    setWeek(week);
  }
  function saved(updated) {
    updateReport(updated);
    setNotice("处理结果已保存，选手可在自己的周记录中查看。");
  }

  return (
    <div className="stack-y-5">
      <header>
        <h2 className="m-0 text-xl font-bold text-text-primary">
          周训练记录与情况说明
        </h2>
      </header>
      <WeekPicker
        report={report}
        loading={loading || saving}
        onChange={changeWeek}
        onReload={reload}
      />
      {error && (
        <div
          role="alert"
          className="rounded-xl bg-[#fff5f5] p-4 text-sm text-error"
        >
          {error}
          <button type="button" onClick={reload} className="ml-3 underline">
            重试
          </button>
        </div>
      )}
      {notice && (
        <p
          role="status"
          className="rounded-xl bg-[#eaf5ed] p-3 text-sm text-[#216e39]"
        >
          {notice}
        </p>
      )}
      {loading ? (
        <div
          role="status"
          className="h-80 animate-pulse rounded-2xl border border-border bg-white"
        >
          <span className="sr-only">正在加载周训练记录</span>
        </div>
      ) : (
        !error && (
          <>
            <section className="grid grid-cols-2 gap-3 xl:grid-cols-4">
              {statistics.map(([label, count]) => (
                <div
                  key={label}
                  className="rounded-2xl border border-border bg-white p-4"
                >
                  <span className="text-xs text-text-secondary">{label}</span>
                  <strong className="mt-3 block text-2xl font-bold text-text-primary">
                    {count}
                  </strong>
                </div>
              ))}
            </section>
            <section className="overflow-hidden rounded-2xl border border-border bg-white shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-4">
                <h3 className="m-0 text-base font-bold text-text-primary">
                  成员周训练明细
                </h3>
                <select
                  aria-label="筛选周训练状态"
                  value={filter}
                  onChange={(event) => setFilter(event.target.value)}
                  className="min-h-10 rounded-xl border border-border bg-bg-secondary px-3 text-sm text-text-primary"
                >
                  <option value="all">全部成员</option>
                  <option value="pending">待审核说明</option>
                  <option value="COMPLETED">已达标</option>
                  <option value="EXEMPT">已豁免</option>
                  <option value="INCOMPLETE">未达标</option>
                  <option value="PENDING">待确认</option>
                  <option value="IN_PROGRESS">进行中</option>
                </select>
              </div>
              {visible.length ? (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[850px] border-collapse text-sm">
                    <thead className="bg-bg-secondary text-left text-xs text-text-secondary">
                      <tr>
                        {[
                          "成员",
                          "每日一题",
                          "每周一赛",
                          "周状态",
                          "情况说明",
                          "操作",
                        ].map((label) => (
                          <th key={label} className="px-4 py-3">
                            {label}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {visible.map((member) => (
                        <tr
                          key={member.userId}
                          className="border-t border-border hover:bg-[#fafbf9]"
                        >
                          <td className="px-4 py-4">
                            <div className="flex items-center gap-3">
                              <UserAvatar user={member} size={36} />
                              <div>
                                <strong className="block text-text-primary">
                                  {member.displayName || member.username}
                                </strong>
                                <small className="block text-text-secondary">
                                  @{member.username}
                                </small>
                                <small
                                  className="block max-w-60 truncate text-text-secondary"
                                  title={[
                                    member.realName,
                                    member.college,
                                    member.className,
                                  ]
                                    .filter(Boolean)
                                    .join(" · ")}
                                >
                                  {[
                                    member.realName,
                                    member.college,
                                    member.className,
                                  ]
                                    .filter(Boolean)
                                    .join(" · ")}
                                </small>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-4 font-bold text-text-primary">
                            {member.dailyCompletedDays} / 7 天
                          </td>
                          <td className="px-4 py-4">
                            <TrainingStatus status={member.contestStatus} />
                          </td>
                          <td className="px-4 py-4">
                            <TrainingStatus status={member.effectiveStatus} />
                            {member.manualStatus !== "AUTO" && (
                              <small className="mt-1 block text-text-secondary">
                                人工调整
                              </small>
                            )}
                          </td>
                          <td className="px-4 py-4">
                            <span
                              className={
                                member.explanation.reviewStatus === "PENDING"
                                  ? "font-bold text-[#8a640a]"
                                  : "text-text-secondary"
                              }
                            >
                              {REVIEW_LABELS[member.explanation.reviewStatus]}
                            </span>
                          </td>
                          <td className="px-4 py-4">
                            <button
                              type="button"
                              onClick={() => {
                                setNotice("");
                                setSelectedId(member.userId);
                              }}
                              className="min-h-9 rounded-xl border border-border px-3 text-xs font-bold text-[#245b8f] hover:bg-bg-secondary"
                            >
                              {member.explanation.reviewStatus === "PENDING"
                                ? "审核说明"
                                : "查看 / 调整"}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState
                  title="暂无符合条件的成员"
                  description="可以切换训练周或调整状态筛选。"
                />
              )}
            </section>
          </>
        )
      )}
      <Dialog.Root
        open={!!selected && !loading && !error}
        onOpenChange={(open) => {
          if (!saving && !open) setSelectedId(null);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-[80] bg-[#081627]/45" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-[81] max-h-[92dvh] w-[calc(100vw-2rem)] max-w-4xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-border bg-white p-4 shadow-xl sm:p-6">
            <div className="mb-2 flex items-start justify-between gap-3">
              <Dialog.Title className="m-0 text-xl font-bold text-text-primary">
                {selected?.realName ||
                  selected?.displayName ||
                  selected?.username}{" "}
                · 周训练记录
              </Dialog.Title>
              <Dialog.Close
                disabled={saving}
                aria-label="关闭成员周训练记录"
                className="rounded-xl p-2 text-text-secondary hover:bg-bg-secondary"
              >
                <X size={18} />
              </Dialog.Close>
            </div>
            <Dialog.Description className="mb-5 mt-0 text-sm text-text-secondary">
              {report?.weekStart} — {report?.weekEnd} · @{selected?.username}
            </Dialog.Description>
            {selected && (
              <>
                <WeeklyTrainingDetails member={selected} />
                {notice && (
                  <p role="status" className="text-sm text-[#216e39]">
                    {notice}
                  </p>
                )}
                <WeeklyReviewForm
                  key={`${report.weekStart}-${selected.userId}-${selected.version}`}
                  week={report.weekStart}
                  member={selected}
                  onUpdated={saved}
                  onSavingChange={setSaving}
                  onReload={reload}
                />
              </>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
