import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  getHeatmap,
  getUserById,
  updateMyProfile,
} from "../api/user";
import { getRatingMeta } from "../utils/cf";
import UserAvatar from "../components/ui/UserAvatar";
import Heatmap from "../components/ui/Heatmap";
import { CardSkeleton } from "../components/ui/Skeleton";
import ProfileEditDialog from "../components/profile/ProfileEditDialog";
import PlatformDataPanel from "../components/profile/PlatformDataPanel";
import { Camera, CalendarRange, Link2, Trash2, UserRound } from "lucide-react";
import PageHeading from "../components/ui/PageHeading";

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [heatmapData, setHeatmapData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  const [avatarSaving, setAvatarSaving] = useState(false);

  useEffect(() => {
    if (!user?.id) return;
    let cancelled = false;
    async function load() {
      setLoading(true);
      setProfile(null);
      try {
        const [userResult, heatmapResult] = await Promise.allSettled([
          getUserById(user.id),
          getHeatmap(user.id, 365),
        ]);
        if (!cancelled) {
          if (userResult.status === "fulfilled" && userResult.value.code === 200) {
            setProfile(userResult.value.data);
          } else {
            setMessage("个人资料加载失败，请刷新后再修改");
          }
          if (heatmapResult.status === "fulfilled" && heatmapResult.value.code === 200) {
            setHeatmapData(heatmapResult.value.data || []);
          }
        }
      } catch {
        if (!cancelled) setMessage("个人资料加载失败，请刷新后再修改");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [user?.id]);

  async function handleAvatarFile(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setMessage("请选择图片文件");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setMessage("头像图片不能超过 5MB");
      return;
    }

    setAvatarSaving(true);
    setMessage("");
    try {
      const avatar = await resizeAvatar(file);
      const resp = await updateMyProfile(user.id, { avatar });
      if (resp.code === 200) {
        setProfile(resp.data);
        updateUser(resp.data);
        setMessage("头像已更新");
      } else {
        setMessage(resp.message || "头像更新失败");
      }
    } catch {
      setMessage("头像处理失败，请更换图片重试");
    } finally {
      setAvatarSaving(false);
    }
  }

  async function handleRemoveAvatar() {
    setAvatarSaving(true);
    setMessage("");
    try {
      const resp = await updateMyProfile(user.id, { avatar: "" });
      if (resp.code === 200) {
        setProfile(resp.data);
        updateUser(resp.data);
        setMessage("头像已移除");
      } else {
        setMessage(resp.message || "头像移除失败");
      }
    } catch {
      setMessage("头像移除失败");
    } finally {
      setAvatarSaving(false);
    }
  }

  const currentProfile = profile || user;
  const ratingMeta = getRatingMeta(currentProfile?.codeforcesRating);

  if (loading) {
    return (
      <div className="stack-y-4">
          <PageHeading icon={UserRound} title="个人中心" />
        <CardSkeleton />
      </div>
    );
  }

  return (
      <div className="profile-page stack-y-5">
        <PageHeading icon={UserRound} title="个人中心" />

      {message && (
        <p
          className={`text-sm rounded-ui px-3 py-2 m-0 ${
            message.includes("成功") || message.includes("已更新")
              ? "bg-[#f0fff0] text-success"
              : "text-text-secondary bg-bg-secondary"
          }`}
        >
          {message}
        </p>
      )}

      {/* CF-style profile card */}
      <div className="bg-white border border-border rounded-ui overflow-hidden">
        <div className="flex items-start gap-4 p-5">
          <div className="profile-avatar-editor">
            <UserAvatar user={currentProfile} size={76} />
            <label className="avatar-upload-button" title="更换头像">
              <Camera size={15} />
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                onChange={handleAvatarFile}
                disabled={avatarSaving}
              />
            </label>
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-lg font-bold m-0 text-text-primary">
              {currentProfile?.displayName || currentProfile?.username || "-"}
            </h2>
            <p className="text-xs text-text-secondary mt-0.5 mb-1">
              @{currentProfile?.username || "-"}
            </p>
            <p className="text-sm m-0" style={{ color: currentProfile?.codeforcesHandle ? ratingMeta.color : undefined }}>
              {currentProfile?.codeforcesHandle
                ? `Codeforces: ${currentProfile.codeforcesHandle} · ${ratingMeta.label}`
                : "尚未绑定 Codeforces"}
            </p>
            {currentProfile?.bio && (
              <p className="text-xs text-text-secondary mt-0.5 m-0">{currentProfile.bio}</p>
            )}
            <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-text-secondary">
              <span>UID: {currentProfile?.uid ?? "-"}</span>
              <span>积分: {currentProfile?.totalPoints ?? 0}</span>
              <span>角色: {currentProfile?.role || "-"}</span>
              <span>
                身份: {currentProfile?.memberType === "ACTIVE_TEAM" ? "现役队员" : "普通成员"}
              </span>
              {currentProfile?.realName && <span>姓名: {currentProfile.realName}</span>}
              {(currentProfile?.college || currentProfile?.className) && (
                <span>{[currentProfile.college, currentProfile.className].filter(Boolean).join(" · ")}</span>
              )}
            </div>
          </div>
          <div className="text-right shrink-0">
            <p className="text-xs text-text-secondary m-0">Codeforces rating</p>
            <p className="text-xl font-bold m-0" style={{ color: ratingMeta.color }}>
              {currentProfile?.codeforcesRating ?? "-"}
            </p>
            <p className="text-xs text-text-secondary m-0">
              max {currentProfile?.maxRating ?? "-"}
            </p>
          </div>
        </div>
        <div className="border-t border-border px-5 py-2.5 flex flex-wrap items-center justify-end gap-2">
          {currentProfile?.avatarUrl && (
            <button
              className="avatar-remove-button"
              onClick={handleRemoveAvatar}
              disabled={avatarSaving}
            >
              <Trash2 size={14} />
              移除头像
            </button>
          )}
          <Link to="/account-binding" className="inline-flex items-center gap-1.5 rounded-ui border border-border bg-white px-3 py-1 text-sm font-semibold text-text-primary hover:bg-bg-secondary">
            <Link2 size={15} />账号绑定
          </Link>
          <Link to="/weekly-training" className="inline-flex items-center gap-1.5 rounded-ui border border-border bg-white px-3 py-1 text-sm font-semibold text-text-primary hover:bg-bg-secondary">
            <CalendarRange size={15} aria-hidden="true" />我的周训练
          </Link>
          <ProfileEditDialog
            profile={profile}
            onSaved={(updatedProfile) => {
              setProfile(updatedProfile);
              updateUser(updatedProfile);
              setMessage("资料已更新");
            }}
          />
        </div>
      </div>

      <PlatformDataPanel profile={currentProfile} />

      <section>
        <h2 className="text-base font-semibold text-text-primary m-0 mb-2">打卡热力图</h2>
        <div className="bg-white border border-border rounded-ui p-5 overflow-hidden">
          <Heatmap data={heatmapData} />
        </div>
      </section>
    </div>
  );
}

function resizeAvatar(file) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const objectUrl = URL.createObjectURL(file);
    image.onload = () => {
      try {
        const size = 320;
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        const context = canvas.getContext("2d");
        const sourceSize = Math.min(image.naturalWidth, image.naturalHeight);
        const sourceX = (image.naturalWidth - sourceSize) / 2;
        const sourceY = (image.naturalHeight - sourceSize) / 2;
        context.drawImage(
          image,
          sourceX,
          sourceY,
          sourceSize,
          sourceSize,
          0,
          0,
          size,
          size
        );
        resolve(canvas.toDataURL("image/webp", 0.82));
      } catch (error) {
        reject(error);
      } finally {
        URL.revokeObjectURL(objectUrl);
      }
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("image load failed"));
    };
    image.src = objectUrl;
  });
}
