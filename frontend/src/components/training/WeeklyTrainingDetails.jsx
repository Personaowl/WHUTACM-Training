import { CalendarDays, CheckCircle2, Circle, Trophy } from "lucide-react";
import {
  readableTime,
  REVIEW_LABELS,
  TRAINING_STATUS,
} from "../../utils/trainingWeek";

export function TrainingStatus({ status }) {
  const meta = TRAINING_STATUS[status] || TRAINING_STATUS.PENDING;
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${meta.color}`}
    >
      {meta.label}
    </span>
  );
}

const WEEKDAYS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
const ACTIONS = {
  SUBMIT: "提交情况说明",
  APPROVE: "同意说明",
  REJECT: "未同意说明",
  ADJUST: "调整周状态",
};

export default function WeeklyTrainingDetails({ member }) {
  const explanation = member.explanation;
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[#eef3f8] p-4">
        <div className="flex flex-wrap items-center gap-3">
          <strong className="text-base text-text-primary">本周训练状态</strong>
          <TrainingStatus status={member.effectiveStatus} />
        </div>
        <span className="text-xs text-text-secondary">
          {member.manualStatus === "AUTO" ? "自动判定" : "管理员调整"} ·
          自动结果：{TRAINING_STATUS[member.automaticStatus]?.label || "待确认"}
        </span>
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between gap-3">
          <h3 className="m-0 flex items-center gap-2 text-base font-bold text-text-primary">
            <CalendarDays size={18} />
            每日一题
          </h3>
          <span className="text-sm font-bold text-text-primary">
            {member.dailyCompletedDays} / 7 天
          </span>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-7">
          {member.days.map((day, index) => (
            <div
              key={day.date}
              className={`min-w-0 rounded-xl border p-3 text-center ${day.completed ? "border-[#cce5d5] bg-[#f0f8f2]" : "border-border bg-bg-secondary"}`}
            >
              <div className="text-xs text-text-secondary">
                {WEEKDAYS[index]}
              </div>
              <div className="mt-1 text-sm font-bold text-text-primary">
                {day.date.slice(5)}
              </div>
              {day.completed ? (
                <CheckCircle2
                  size={22}
                  className="mx-auto my-3 text-[#31804c]"
                />
              ) : (
                <Circle size={22} className="mx-auto my-3 text-[#b4bfcb]" />
              )}
              <div className="text-xs font-semibold text-text-primary">
                {day.completed
                  ? "已完成"
                  : day.status === "UPCOMING"
                    ? "待开始"
                    : day.status === "IN_PROGRESS"
                      ? "今日待完成"
                      : "未完成"}
              </div>
              <div className="mt-1 min-h-4 text-[10px] text-text-secondary">
                {day.completed
                  ? [day.easyCompleted && "Easy", day.hardCompleted && "Hard"]
                      .filter(Boolean)
                      .join(" · ") || "已校验"
                  : "—"}
              </div>
            </div>
          ))}
        </div>
        <p className="m-0 mt-2 text-xs text-text-secondary">
          每天至少完成一道每日题即计为完成一天。
        </p>
      </section>

      <section className="rounded-xl border border-border p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h3 className="m-0 flex items-center gap-2 text-base font-bold text-text-primary">
            <Trophy size={18} />
            每周一赛 · AtCoder ABC
          </h3>
          <TrainingStatus status={member.contestStatus} />
        </div>
        {member.contests.length ? (
          <div className="space-y-3">
            {member.contests.map((contest) => (
              <div
                key={contest.contestId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-bg-secondary p-3"
              >
                <div>
                  <a
                    href={contest.url}
                    target="_blank"
                    rel="noreferrer"
                    className="text-sm font-bold text-text-primary hover:underline"
                  >
                    {contest.name} ↗
                  </a>
                  <p className="m-0 mt-1 text-xs text-text-secondary">
                    赛时 AC：{contest.acCount ?? "—"}
                    {contest.checkedAt
                      ? ` · 最近同步 ${readableTime(contest.checkedAt)}`
                      : " · 等待同步结果"}
                  </p>
                </div>
                <TrainingStatus status={contest.status} />
              </div>
            ))}
          </div>
        ) : (
          <p className="m-0 text-sm text-text-secondary">
            本周尚无已同步的 ABC 场次，等待确认；不会直接判为缺席。
          </p>
        )}
        <p className="m-0 mt-3 text-xs text-text-secondary">
          周内有多场 ABC
          时，任一场达到平台要求即可。比赛结果可能在赛后延迟更新。
        </p>
      </section>

      <section className="rounded-xl border border-border p-4">
        <div className="flex items-center justify-between gap-3">
          <h3 className="m-0 text-base font-bold text-text-primary">
            情况说明与处理
          </h3>
          <span
            className={`text-xs font-bold ${explanation.reviewStatus === "PENDING" ? "text-[#8a640a]" : "text-text-secondary"}`}
          >
            {REVIEW_LABELS[explanation.reviewStatus]}
          </span>
        </div>
        {explanation.content ? (
          <>
            <p className="mb-1 mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-text-primary">
              {explanation.content}
            </p>
            <small className="text-text-secondary">
              提交于 {readableTime(explanation.submittedAt)}
            </small>
          </>
        ) : (
          <p className="mb-0 mt-3 text-sm text-text-secondary">
            如有请假、比赛冲突等特殊情况，可提交说明供管理员处理。
          </p>
        )}
        {explanation.reviewNote && (
          <div className="mt-3 rounded-xl bg-[#f0f4f8] p-3">
            <div className="text-xs font-bold text-[#355778]">
              最新管理员意见 · {explanation.reviewer || "管理员"}
            </div>
            <p className="mb-1 mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-text-primary">
              {explanation.reviewNote}
            </p>
            <small className="text-text-secondary">
              {readableTime(explanation.reviewedAt)}
            </small>
          </div>
        )}
        {!!member.history.length && (
          <details className="mt-4 border-t border-border pt-3">
            <summary className="cursor-pointer text-xs font-bold text-text-secondary">
              查看处理记录（最近 {member.history.length} 条）
            </summary>
            <ol className="m-0 mt-3 list-none space-y-3 p-0">
              {member.history.map((entry) => (
                <li key={entry.id} className="border-l-2 border-[#d4dfeb] pl-3">
                  <div className="text-xs font-bold text-text-primary">
                    {ACTIONS[entry.action] || entry.action} · {entry.actor}
                  </div>
                  <p className="my-1 whitespace-pre-wrap break-words text-sm text-text-secondary">
                    {entry.message}
                  </p>
                  <small className="text-text-secondary">
                    {readableTime(entry.createdAt)}
                    {entry.resultStatus
                      ? ` · 周状态：${TRAINING_STATUS[entry.resultStatus]?.label || entry.resultStatus}`
                      : ""}
                  </small>
                </li>
              ))}
            </ol>
          </details>
        )}
      </section>
    </div>
  );
}
