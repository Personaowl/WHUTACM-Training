import { useEffect, useState } from "react";
import { ArrowLeft, ExternalLink } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { getPublicProfile } from "../api/user";
import CodeforcesOverview from "../components/profile/CodeforcesOverview";
import UserAvatar from "../components/ui/UserAvatar";
import { CardSkeleton } from "../components/ui/Skeleton";
import { getRatingMeta } from "../utils/cf";

export default function MemberProfilePage() {
  const { id } = useParams();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const response = await getPublicProfile(id);
        if (!cancelled) {
          if (response.code === 200) setProfile(response.data);
          else setError(response.message || "成员不存在");
        }
      } catch {
        if (!cancelled) setError("成员资料加载失败");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loading) {
    return (
      <div className="stack-y-4">
        <CardSkeleton />
        <CardSkeleton />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="text-center py-16">
        <p className="text-error">{error || "成员不存在"}</p>
        <Link to="/leaderboard" className="text-sm text-text-secondary">
          返回排行榜
        </Link>
      </div>
    );
  }

  const ratingMeta = getRatingMeta(profile.codeforcesRating);
  return (
    <div className="stack-y-5">
      <Link
        to="/leaderboard"
        className="inline-flex items-center gap-1 text-sm text-text-secondary hover:text-text-primary no-underline"
      >
        <ArrowLeft size={15} /> 返回排行榜
      </Link>

      <section className="bg-white border border-border rounded-ui p-5">
        <div className="flex flex-col sm:flex-row sm:items-center gap-4">
          <UserAvatar user={profile} size={88} />
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-bold text-text-primary m-0">
                {profile.displayName}
              </h1>
              <span className="text-[11px] px-2 py-0.5 rounded-full border border-border text-text-secondary">
                {profile.memberType === "ACTIVE_TEAM" ? "现役队员" : "普通成员"}
              </span>
            </div>
            {profile.bio && (
              <p className="text-sm text-text-secondary mt-2 mb-0">{profile.bio}</p>
            )}
            <p className="text-xs text-text-secondary mt-2 mb-0">
              站内积分 {profile.totalPoints ?? 0}
            </p>
          </div>
          {profile.codeforcesHandle && (
            <a
              href={`https://codeforces.com/profile/${encodeURIComponent(profile.codeforcesHandle)}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-sm font-semibold no-underline hover:underline"
              style={{ color: ratingMeta.color }}
            >
              {profile.codeforcesHandle} · {profile.codeforcesRating ?? "Unrated"}
              <ExternalLink size={13} />
            </a>
          )}
        </div>
      </section>

      <CodeforcesOverview userId={profile.id} handle={profile.codeforcesHandle} />
    </div>
  );
}
