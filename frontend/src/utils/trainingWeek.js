export function mondayOf(date) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date || "")) return "";
  const parsed = new Date(`${date}T00:00:00Z`);
  if (
    Number.isNaN(parsed.getTime()) ||
    parsed.toISOString().slice(0, 10) !== date
  )
    return "";
  parsed.setUTCDate(parsed.getUTCDate() - ((parsed.getUTCDay() + 6) % 7));
  return parsed.toISOString().slice(0, 10);
}

export function shiftWeek(monday, amount) {
  const normalized = mondayOf(monday);
  if (!normalized) return "";
  const date = new Date(`${normalized}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + amount * 7);
  return date.toISOString().slice(0, 10);
}

export const TRAINING_STATUS = {
  AUTO: { label: "自动判定", color: "bg-bg-secondary text-text-secondary" },
  COMPLETED: { label: "已达标", color: "bg-[#e8f6ec] text-[#216e39]" },
  EXEMPT: { label: "已豁免", color: "bg-[#e9f1fa] text-[#245b8f]" },
  INCOMPLETE: { label: "未达标", color: "bg-[#fff0ed] text-[#ab4431]" },
  IN_PROGRESS: { label: "进行中", color: "bg-[#edf2f8] text-[#355778]" },
  PENDING: { label: "待确认", color: "bg-[#fff6df] text-[#8a640a]" },
  DATA_ERROR: { label: "数据异常", color: "bg-[#fff6df] text-[#8a640a]" },
  NOT_REQUIRED: {
    label: "无需参加",
    color: "bg-bg-secondary text-text-secondary",
  },
  UNBOUND: { label: "未绑定账号", color: "bg-[#fff0ed] text-[#ab4431]" },
  PARTICIPATED: {
    label: "已参赛·未达标",
    color: "bg-[#fff6df] text-[#8a640a]",
  },
  ABSENT: { label: "缺席", color: "bg-[#fff0ed] text-[#ab4431]" },
  UPCOMING: { label: "待开始", color: "bg-bg-secondary text-text-secondary" },
};

export const REVIEW_LABELS = {
  NONE: "未提交说明",
  PENDING: "待审核",
  APPROVED: "已同意",
  REJECTED: "未同意",
};

export function readableTime(value) {
  if (!value) return "";
  return value.replace("T", " ").slice(0, 16);
}
