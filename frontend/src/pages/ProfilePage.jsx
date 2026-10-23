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
import CodeforcesOverview from "../components/profile/CodeforcesOverview";
import { Camera, ClipboardCheck, Link2, Trash2 } from "lucide-react";
import "./ProfilePage.css";

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
      <div className="profile-page stack-y-4">
        <h1 className="page-title">个人中心</h1>
        <CardSkeleton />
      </div>
    );
  }

  return (
    <div className="profile-page stack-y-5">
      <h1 className="page-title">个人中心</h1>

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

      <section className="profile-identity-card" aria-label="个人资料">
        <div className="profile-main-row">
          <div className="profile-avatar-editor">
            <UserAvatar user={currentProfile} size={80} />
            <label className="avatar-upload-button" title="更换头像">
              <Camera size={16} />
              <input
                type="file"
                aria-label="更换头像"
                accept="image/png,image/jpeg,image/webp,image/gif"
                onChange={handleAvatarFile}
                disabled={avatarSaving}
              />
            </label>
          </div>
          <div className="profile-identity">
            <h2>
              {currentProfile?.displayName || currentProfile?.username || "-"}
            </h2>
            <p className="profile-username">
              @{currentProfile?.username || "-"}
            </p>
            <p className="profile-cf-account" style={{ color: currentProfile?.codeforcesHandle ? ratingMeta.color : undefined }}>
              {currentProfile?.codeforcesHandle
                ? `Codeforces: ${currentProfile.codeforcesHandle} · ${ratingMeta.label}`
                : "尚未绑定 Codeforces"}
            </p>
          </div>
          <div className="profile-points">
            <span>训练积分</span>
            <strong>{Number(currentProfile?.totalPoints ?? 0).toLocaleString("en-US")}</strong>
          </div>
        </div>
        <dl className="profile-details">
          <div><dt>UID</dt><dd>{currentProfile?.uid ?? "—"}</dd></div>
          <div><dt>角色</dt><dd>{{ SUPER_ADMIN: "超级管理员", ADMIN: "管理员", USER: "选手" }[currentProfile?.role] || currentProfile?.role || "—"}</dd></div>
          <div><dt>身份</dt><dd>{currentProfile?.memberType === "ACTIVE_TEAM" ? "现役队员" : "普通成员"}</dd></div>
          {currentProfile?.realName && <div><dt>姓名</dt><dd>{currentProfile.realName}</dd></div>}
          {currentProfile?.college && <div><dt>学院</dt><dd>{currentProfile.college}</dd></div>}
          {currentProfile?.className && <div><dt>班级</dt><dd>{currentProfile.className}</dd></div>}
        </dl>
        {currentProfile?.bio && <p className="profile-bio">{currentProfile.bio}</p>}
        <div className="profile-actions">
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
          <Link to="/account-binding" className="profile-action-link">
            <Link2 size={15} />账号绑定
          </Link>
          <Link to="/weekly-training" className="profile-action-link">
            <ClipboardCheck size={15} />我的周训练
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
      </section>

      <CodeforcesOverview
        userId={currentProfile?.id}
        handle={currentProfile?.codeforcesHandle}
        canRefresh
      />

      <section className="profile-heatmap-section">
        <h2>打卡热力图</h2>
        <div className="profile-heatmap-panel">
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
