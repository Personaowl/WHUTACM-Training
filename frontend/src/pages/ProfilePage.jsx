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
import EmptyState from "../components/ui/EmptyState";
import CodeforcesOverview from "../components/profile/CodeforcesOverview";
import { Camera, Link2, Trash2 } from "lucide-react";

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [heatmapData, setHeatmapData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  const [editMode, setEditMode] = useState(false);
  const [editUsername, setEditUsername] = useState("");
  const [editEmail, setEditEmail] = useState("");
  const [editPassword, setEditPassword] = useState("");
  const [editDisplayName, setEditDisplayName] = useState("");
  const [editRealName, setEditRealName] = useState("");
  const [editCollege, setEditCollege] = useState("");
  const [editClassName, setEditClassName] = useState("");
  const [editBio, setEditBio] = useState("");
  const [saving, setSaving] = useState(false);
  const [avatarSaving, setAvatarSaving] = useState(false);

  useEffect(() => {
    if (!user?.id) return;
    let cancelled = false;
    async function load() {
      try {
        const [userResp, heatmapResp] = await Promise.all([
          getUserById(user.id),
          getHeatmap(user.id, 365),
        ]);
        if (!cancelled) {
          if (userResp.code === 200) {
            setProfile(userResp.data);
          }
          if (heatmapResp.code === 200) setHeatmapData(heatmapResp.data || []);
        }
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [user?.id]);

  function enterEditMode() {
    setEditUsername(profile?.username || "");
    setEditEmail(profile?.email || "");
    setEditPassword("");
    setEditDisplayName(profile?.displayName || "");
    setEditRealName(profile?.realName || "");
    setEditCollege(profile?.college || "");
    setEditClassName(profile?.className || "");
    setEditBio(profile?.bio || "");
    setEditMode(true);
    setMessage("");
  }

  function cancelEdit() {
    setEditMode(false);
    setMessage("");
  }

  async function handleSave() {
    setSaving(true);
    setMessage("");
    try {
      const payload = {};
      if (editUsername.trim()) payload.username = editUsername.trim();
      if (editEmail.trim()) payload.email = editEmail.trim();
      else payload.email = null;
      if (editPassword.trim()) payload.password = editPassword;
      payload.displayName = editDisplayName.trim() || null;
      payload.realName = editRealName.trim() || null;
      payload.college = editCollege.trim() || null;
      payload.className = editClassName.trim() || null;
      payload.bio = editBio.trim() || null;

      const resp = await updateMyProfile(user.id, payload);
      if (resp.code === 200) {
        updateUser(resp.data);
        setProfile(resp.data);
        setEditMode(false);
        setMessage("资料已更新");
      } else {
        setMessage(resp.message || "更新失败");
      }
    } catch {
      setMessage("更新请求失败");
    } finally {
      setSaving(false);
    }
  }

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
      <div className="space-y-4">
        <h1 className="text-lg font-semibold">个人中心</h1>
        <CardSkeleton />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold text-text-primary m-0">个人中心</h1>

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
          <div className="text-right flex-shrink-0">
            <p className="text-xs text-text-secondary m-0">Codeforces rating</p>
            <p className="text-xl font-bold m-0" style={{ color: ratingMeta.color }}>
              {currentProfile?.codeforcesRating ?? "-"}
            </p>
            <p className="text-xs text-text-secondary m-0">
              max {currentProfile?.maxRating ?? "-"}
            </p>
          </div>
        </div>
        <div className="border-t border-border px-5 py-2.5 flex justify-end">
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
          <Link to="/account-binding" className="mr-2 inline-flex items-center gap-1.5 rounded-ui border border-border bg-white px-3 py-1 text-sm font-semibold text-text-primary hover:bg-bg-secondary">
            <Link2 size={15} />账号绑定
          </Link>
          {!editMode ? (
            <button
              className="px-3 py-1 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer"
              onClick={enterEditMode}
            >
              编辑资料
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                className="px-3 py-1 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer"
                onClick={cancelEdit}
                disabled={saving}
              >
                取消
              </button>
              <button
                className="px-3 py-1 text-sm text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer disabled:opacity-50"
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? "保存中..." : "保存"}
              </button>
            </div>
          )}
        </div>
      </div>

      <CodeforcesOverview
        userId={currentProfile?.id}
        handle={currentProfile?.codeforcesHandle}
        canRefresh
      />

      {/* Edit form */}
      {editMode && (
        <div className="bg-white border border-border rounded-ui p-4 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">登录账号</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                value={editUsername}
                onChange={(e) => setEditUsername(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">邮箱</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                type="email"
                value={editEmail}
                onChange={(e) => setEditEmail(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">新密码（留空不修改）</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                type="password"
                value={editPassword}
                onChange={(e) => setEditPassword(e.target.value)}
                placeholder="至少 8 位"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">用户名（展示名称）</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                value={editDisplayName}
                onChange={(e) => setEditDisplayName(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">姓名（校内管理用，可选）</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                value={editRealName}
                onChange={(e) => setEditRealName(e.target.value)}
                placeholder="例如：张三"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">学院（可选）</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                value={editCollege}
                onChange={(e) => setEditCollege(e.target.value)}
                placeholder="例如：计算机与人工智能学院"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-text-secondary">班级（可选）</span>
              <input
                className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary"
                value={editClassName}
                onChange={(e) => setEditClassName(e.target.value)}
                placeholder="例如：计科 2301 班"
              />
            </label>
          </div>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-text-secondary">个人简介</span>
            <textarea
              className="px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary resize-y"
              rows={2}
              value={editBio}
              onChange={(e) => setEditBio(e.target.value)}
            />
          </label>
        </div>
      )}

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
