import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { updateMyProfile } from "../../api/user";
import { createProfileDraft, createProfileUpdate } from "../../utils/profileForm";

const FIELDS = [
  { name: "username", label: "登录账号", required: true, maxLength: 64, autoComplete: "username" },
  { name: "displayName", label: "用户名（展示名称）", maxLength: 100 },
  { name: "realName", label: "姓名（选填）", maxLength: 100, autoComplete: "name" },
  { name: "college", label: "学院（选填）", maxLength: 150 },
  { name: "className", label: "班级（选填）", maxLength: 150 },
  { name: "email", label: "邮箱（选填）", type: "email", maxLength: 255, autoComplete: "email" },
  { name: "password", label: "新密码（留空不修改）", type: "password", minLength: 8, maxLength: 64, autoComplete: "new-password" },
];

export default function ProfileEditDialog({ profile, onSaved }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(() => createProfileDraft(profile));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  function handleOpenChange(nextOpen) {
    if (saving) return;
    setDraft(createProfileDraft(profile));
    setError("");
    setOpen(nextOpen);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (saving || !profile?.id) return;
    if (!draft.username.trim()) {
      setError("登录账号不能为空");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const response = await updateMyProfile(profile.id, createProfileUpdate(draft));
      if (response.code !== 200) {
        setError(response.message || "资料保存失败，请重试");
        return;
      }
      onSaved(response.data);
      setDraft(createProfileDraft(response.data));
      setOpen(false);
    } catch {
      setError("资料保存失败，请检查网络后重试");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Trigger asChild>
        <button type="button" disabled={!profile?.id} className="px-3 py-1 text-sm border border-border rounded-ui bg-white text-text-primary hover:bg-bg-secondary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed">
          修改个人资料
        </button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/35 z-40" />
        <Dialog.Content className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-50 flex max-h-[90dvh] w-[560px] max-w-[calc(100vw-24px)] flex-col rounded-ui border border-border bg-white p-5 shadow-xl sm:p-6">
          <div className="flex items-center justify-between gap-3 mb-2">
            <Dialog.Title className="m-0 text-lg font-bold text-text-primary">修改个人资料</Dialog.Title>
            <Dialog.Close disabled={saving} aria-label="关闭资料编辑" className="flex items-center justify-center rounded-full border-0 bg-bg-secondary p-2 text-text-secondary cursor-pointer disabled:opacity-50">
              <X size={18} />
            </Dialog.Close>
          </div>
          <Dialog.Description className="m-0 mb-4 text-sm text-text-secondary">
            姓名、学院和班级为选填信息，可以随时修改。
          </Dialog.Description>
          <form onSubmit={handleSubmit} className="min-h-0 overflow-y-auto space-y-4 pr-1">
            <fieldset disabled={saving} className="m-0 min-w-0 border-0 p-0 grid grid-cols-1 sm:grid-cols-2 gap-3">
              {FIELDS.map(({ name, label, ...attributes }) => (
                <label key={name} className="flex min-w-0 flex-col gap-1">
                  <span className="text-sm text-text-secondary">{label}</span>
                  <input
                    {...attributes}
                    name={name}
                    value={draft[name]}
                    onChange={(event) => setDraft((current) => ({ ...current, [name]: event.target.value }))}
                    className="w-full box-border px-3 py-2 text-sm border border-border rounded-ui bg-white text-text-primary outline-none focus:border-text-primary"
                  />
                </label>
              ))}
              <label className="flex flex-col gap-1 sm:col-span-2">
                <span className="text-sm text-text-secondary">个人简介</span>
                <textarea value={draft.bio} maxLength={1000} rows={3}
                  onChange={(event) => setDraft((current) => ({ ...current, bio: event.target.value }))}
                  className="w-full box-border px-3 py-2 text-sm border border-border rounded-ui outline-none focus:border-text-primary resize-y" />
              </label>
            </fieldset>
            {error && <p role="alert" className="m-0 text-sm text-error">{error}</p>}
            <div className="sticky bottom-0 flex justify-end gap-2 border-t border-border bg-white py-3">
              <button type="button" onClick={() => handleOpenChange(false)} disabled={saving}
                className="px-4 py-2 text-sm border border-border rounded-ui bg-white text-text-primary cursor-pointer disabled:opacity-50">取消</button>
              <button type="submit" disabled={saving} className="button-primary px-4 py-2 text-sm disabled:opacity-50">
                {saving ? "保存中..." : "保存修改"}
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
