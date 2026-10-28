import { getRatingMeta } from "./cf.js";
import { availablePlatformMetrics, platformProfileUrl } from "./platformPanel.js";
import { matchingPlatformOverview, platformAccountLabel } from "./platformOverview.js";
import { ratingBand, ratingBandRange } from "./ratingPalette.js";

const THEMES = {
  codeforces: { accent: "#285eab", tint: "#cfe0ff", source: "Codeforces API" },
  atcoder: { accent: "#965419", tint: "#ffddb6", source: "AtCoder / AtCoder Problems" },
  luogu: { accent: "#15785f", tint: "#bcebdc", source: "洛谷公开资料" },
  nowcoder: { accent: "#7248a6", tint: "#e4d2fa", source: "Nowcoder 公开资料" },
};

const FACT_KEYS = {
  codeforces: ["organization","location","realName","ratedContestCount","friendOfCount","contribution","registrationTimeSeconds","lastOnlineTimeSeconds","attemptedCount","acceptedSubmissionCount"],
  atcoder: ["globalRank","ratedContestCount","country","organization","lastCompetedDate"],
  luogu: ["followingCount","followerCount","attemptedCount","registrationTimeSeconds"],
  nowcoder: ["globalRank","followingCount","followerCount","attemptedCount","submissionCount"],
};
const FACT_LABELS = {
  organization:"所属组织",location:"所在地",realName:"公开姓名",country:"国家 / 地区",globalRank:"平台排名",
  ratedContestCount:"Rated 比赛",friendOfCount:"被加为好友",contribution:"贡献",followingCount:"关注",followerCount:"粉丝",
  attemptedCount:"尝试题目",acceptedSubmissionCount:"AC 提交（样本）",submissionCount:"提交次数",
  registrationTimeSeconds:"注册时间（UTC+8）",lastOnlineTimeSeconds:"最近在线（UTC+8）",lastCompetedDate:"最近参赛",
};
const TEXT_FACTS = new Set(["organization","location","realName","country","lastCompetedDate"]);
const clean = value => typeof value === "string" ? [...value.replace(/[\p{Cc}\p{Cf}]/gu,"").replace(/WHUT(?:-ACM)?-[a-f0-9]{8,64}/gi,"").trim()].slice(0,128).join("") : "";

export function platformProfileFacts(platform, profileInfo) {
  const raw=profileInfo?.fields;
  if(!raw || typeof raw!=="object" || Array.isArray(raw))return [];
  const fields={...raw,location:[clean(raw.city),clean(raw.country)].filter(Boolean).join(", ")};
  return (FACT_KEYS[platform]||[]).flatMap(key=>{
    let value=fields[key],kind="number";
    if(TEXT_FACTS.has(key)){
      value=clean(value);kind="text";
      if(key==="lastCompetedDate"&&!/^\d{4}-\d{2}-\d{2}$/.test(value))return [];
      if(!value)return [];
    }else{
      if(!["string","number"].includes(typeof value)||! /^-?\d+$/.test(String(value)))return [];
      value=Number(value);
      if(!Number.isSafeInteger(value)||(value<0&&key!=="contribution")||(key==="globalRank"&&value===0))return [];
      if(key.endsWith("TimeSeconds")){
        const time=new Date(value*1000);if(value<=0||Number.isNaN(time.getTime()))return [];
        value=time.toLocaleString("zh-CN",{timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"});kind="time";
      }else value=formatNumber(value);
    }
    let label=FACT_LABELS[key];
    if(key==="globalRank")label=platform==="atcoder"?"算法排名":"Rating 排名";
    if(key==="attemptedCount"&&platform==="codeforces")label="尝试题目（样本）";
    return [{key,label,value,kind}];
  });
}
const formatNumber=value=>value.toLocaleString("en-US");

export function platformStatisticRows(facts = []) {
  const practiceKeys = new Set(["attemptedCount", "acceptedSubmissionCount", "submissionCount"]);
  return [
    { key: "community", label: "参赛与社区统计", facts: facts.filter(item => !practiceKeys.has(item.key)) },
    { key: "practice", label: "练习统计", facts: facts.filter(item => practiceKeys.has(item.key)) },
  ].filter(row => row.facts.length > 0);
}

// A presentation of the existing verified overview; this model never fetches data.
export function platformSnapshotModel(platform, accountId, overview) {
  if (!accountId || !THEMES[platform]) return null;
  const matched = matchingPlatformOverview(overview, accountId);
  const data = matched && (!matched.platform || matched.platform === platform) ? matched : null;
  const metrics = availablePlatformMetrics(platform, data).filter(item => platform !== "luogu" || item.key === "solvedCount");
  const currentBand = platform !== "luogu" && ratingBand(platform, data?.rating);
  const theme = currentBand ? { accent: currentBand.ink, tint: currentBand.background } : THEMES[platform];
  const name = platformAccountLabel(platform, accountId, data);
  const rank = currentBand && platform === "codeforces" ? getRatingMeta(data.rating).label : null;
  const facts=platformProfileFacts(platform,data?.profileInfo);
  const identityKeys = new Set(["organization", "location", "country"]);
  const accountKeys = platform === "nowcoder" ? new Set(["globalRank", "followingCount", "followerCount"]) : new Set();
  return {
    name,
    accountId: String(accountId),
    showUid: ["luogu", "nowcoder"].includes(platform) && name !== `UID ${accountId}`,
    url: platformProfileUrl(platform, accountId),
    accent: theme.accent,
    tint: theme.tint,
    source: THEMES[platform].source,
    rank,
    rankRange: currentBand ? ratingBandRange(currentBand) : null,
    facts,
    factGroups: {
      identity: facts.filter(item => identityKeys.has(item.key)),
      statistics: facts.filter(item => item.kind === "number" && !accountKeys.has(item.key)),
      records: facts.filter(item => accountKeys.has(item.key) || (!identityKeys.has(item.key) && item.kind !== "number")),
    },
    highlights: facts.filter(item=>item.kind==="number").slice(0,3),
    metrics: metrics.map(item => ({ ...item, color: item.key === "solvedCount" ? null : ratingBand(platform, item.value)?.ink,
      rank: item.key !== "solvedCount" && platform === "codeforces" ? getRatingMeta(item.value).label : null })),
  };
}
