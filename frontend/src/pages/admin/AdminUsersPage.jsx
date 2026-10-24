import { useEffect, useMemo, useState } from "react";
import { listUsers } from "../../api/user";
import {
  adminCreateUser,
  changeUserMemberType,
  changeUserRole,
  getRoles,
  resetUserPassword,
} from "../../api/admin";
import { useAuth } from "../../context/AuthContext";
import UserAvatar from "../../components/ui/UserAvatar";
import { ListSkeleton } from "../../components/ui/Skeleton";
import EmptyState from "../../components/ui/EmptyState";
import * as Select from "@radix-ui/react-select";
import * as Dialog from "@radix-ui/react-dialog";
import * as VisuallyHidden from "@radix-ui/react-visually-hidden";
import { Search, Users } from "lucide-react";
import PageHeading from "../../components/ui/PageHeading";

export default function AdminUsersPage() {
  const { user, isSuperAdmin } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [search, setSearch] = useState("");
  const [resettingUserId, setResettingUserId] = useState(null);

  // Create user modal
  const [createOpen, setCreateOpen] = useState(false);
  const [createUsername, setCreateUsername] = useState("");
  const [createEmail, setCreateEmail] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [createRole, setCreateRole] = useState("USER");
  const [roles, setRoles] = useState([]);

  useEffect(() => { loadUsers(); loadRoles(); }, []);

  async function loadUsers() {
    try {
      const resp = await listUsers();
      if (resp.code === 200) setUsers(resp.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  async function loadRoles() {
    try {
      const resp = await getRoles();
      if (resp.code === 200) setRoles(resp.data || []);
    } catch { /* ignore */ }
  }

  async function handleCreate() {
    if (!createUsername.trim() || !createPassword.trim()) {
      setMessage("用户名和密码不能为空");
      return;
    }
    try {
      const resp = await adminCreateUser({
        username: createUsername.trim(),
        email: createEmail.trim() || `${createUsername.trim()}@whut.local`,
        password: createPassword,
        role: createRole,
      });
      if (resp.code === 200) {
        setCreateOpen(false);
        setCreateUsername("");
        setCreateEmail("");
        setCreatePassword("");
        setCreateRole("USER");
        setMessage("用户创建成功");
        loadUsers();
      } else {
        setMessage(resp.message || "创建失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleChangeRole(userId, newRole) {
    try {
      const resp = await changeUserRole(userId, newRole);
      if (resp.code === 200) {
        setMessage("角色已更新");
        loadUsers();
      } else {
        setMessage(resp.message || "操作失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleChangeMemberType(userId, memberType) {
    try {
      const resp = await changeUserMemberType(userId, memberType);
      if (resp.code === 200) {
        setMessage("成员身份已更新");
        loadUsers();
      } else {
        setMessage(resp.message || "操作失败");
      }
    } catch {
      setMessage("请求失败");
    }
  }

  async function handleResetPassword(target) {
    if (!window.confirm(`确认将 ${target.displayName || target.username} 的密码重置为 123456？该用户当前所有登录将立即失效。`)) {
      return;
    }
    setResettingUserId(target.id);
    setMessage("");
    try {
      const resp = await resetUserPassword(target.id);
      if (resp.code === 200) {
        setMessage(`已将 ${target.displayName || target.username} 的密码重置为 123456，并注销其旧登录。`);
      } else {
        setMessage(resp.message || "密码重置失败");
      }
    } catch {
      setMessage("密码重置失败");
    } finally {
      setResettingUserId(null);
    }
  }

  const filteredUsers = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase();
    if (!keyword) return users;
    return users.filter((item) => [
      item.realName,
      item.college,
      item.displayName,
      item.username,
    ].some((value) => value?.toLocaleLowerCase().includes(keyword)));
  }, [search, users]);

  return (
    <div className="stack-y-4">
      <div className="flex items-center justify-between">
        <PageHeading icon={Users} title="用户管理" />
        <button
          className="px-3 py-1.5 text-sm font-medium text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
          onClick={() => setCreateOpen(true)}
        >
          创建用户
        </button>
      </div>

      {message && (
        <p className="text-sm bg-bg-secondary text-text-secondary rounded-ui px-3 py-2 m-0">{message}</p>
      )}

      <div className="relative max-w-md">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary" />
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          className="w-full pl-9 pr-3 py-2 text-sm border border-border rounded-ui bg-white outline-hidden focus:border-text-primary"
          placeholder="搜索姓名、学院或用户名"
          aria-label="搜索姓名、学院或用户名"
        />
      </div>

      {loading ? (
        <ListSkeleton rows={8} />
      ) : filteredUsers.length === 0 ? (
        <EmptyState title={search ? "未找到匹配用户" : "暂无用户"} />
      ) : (
        <div className="bg-white border border-border rounded-ui overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-bg-secondary">
                  <th className="text-left px-4 py-2.5 font-medium text-text-secondary">用户 / 校内资料</th>
                  <th className="text-left px-4 py-2.5 font-medium text-text-secondary">邮箱</th>
                  <th className="text-left px-4 py-2.5 font-medium text-text-secondary">角色</th>
                  <th className="text-left px-4 py-2.5 font-medium text-text-secondary">成员身份</th>
                  <th className="text-right px-4 py-2.5 font-medium text-text-secondary">积分</th>
                  {isSuperAdmin && (
                    <th className="text-right px-4 py-2.5 font-medium text-text-secondary">操作</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="border-b border-border last:border-0 hover:bg-bg-secondary">
                    <td className="px-4 py-2.5">
                      <div className="flex items-center gap-2">
                        <UserAvatar user={u} size={24} />
                        <span>
                          <strong className="block text-text-primary font-medium">{u.displayName || u.username}</strong>
                          <small className="block text-[11px] text-text-secondary">
                            @{u.username}{u.realName ? ` · ${u.realName}` : ""}
                            {(u.college || u.className) ? ` · ${[u.college, u.className].filter(Boolean).join(" · ")}` : ""}
                          </small>
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-2.5 text-text-secondary">{u.email || "-"}</td>
                    <td className="px-4 py-2.5">
                      <span className={`text-xs px-2 py-0.5 rounded-full ${
                        u.role === "SUPER_ADMIN" ? "bg-[#fff0f0] text-error" :
                        u.role === "ADMIN" ? "bg-[#fff8f0] text-warning" :
                        "bg-bg-secondary text-text-secondary"
                      }`}>
                        {u.role || "USER"}
                      </span>
                    </td>
                    <td className="px-4 py-2.5">
                      <MemberTypeSelect
                        currentMemberType={u.memberType || "REGULAR"}
                        onChange={(memberType) => handleChangeMemberType(u.id, memberType)}
                      />
                    </td>
                    <td className="px-4 py-2.5 text-right font-medium">{u.totalPoints ?? 0}</td>
                    {isSuperAdmin && (
                      <td className="px-4 py-2.5 text-right">
                        <div className="inline-flex items-center justify-end gap-2">
                          <RoleSelect
                            currentRole={u.role || "USER"}
                            roles={roles}
                            onChange={(role) => handleChangeRole(u.id, role)}
                          />
                          {u.id !== user?.id && u.role !== "SUPER_ADMIN" && (
                            <button
                              type="button"
                              className="px-2 py-1 text-xs font-medium text-error border border-[#f3b4b4] bg-white rounded-sm hover:bg-[#fff0f0] cursor-pointer disabled:opacity-50"
                              disabled={resettingUserId === u.id}
                              onClick={() => handleResetPassword(u)}
                            >
                              {resettingUserId === u.id ? "重置中…" : "重置密码"}
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create user modal */}
      <Dialog.Root open={createOpen} onOpenChange={setCreateOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 bg-black/30 z-40" />
          <Dialog.Content className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-white rounded-ui shadow-lg border border-border p-6 w-[420px] max-w-[90vw] z-50">
            <VisuallyHidden.Root>
              <Dialog.Title>创建用户</Dialog.Title>
            </VisuallyHidden.Root>
            <h3 className="text-base font-semibold text-text-primary m-0 mb-4">创建用户</h3>
            <div className="stack-y-3">
              <input className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary"
                value={createUsername} onChange={(e) => setCreateUsername(e.target.value)} placeholder="用户名 *" />
              <input className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary"
                type="email" value={createEmail} onChange={(e) => setCreateEmail(e.target.value)} placeholder="邮箱" />
              <input className="w-full px-3 py-2 text-sm border border-border rounded-ui outline-hidden focus:border-text-primary"
                type="password" value={createPassword} onChange={(e) => setCreatePassword(e.target.value)} placeholder="密码 * (至少8位)" />
              <select
                className="w-full px-3 py-2 text-sm border border-border rounded-ui bg-white outline-hidden focus:border-text-primary"
                value={createRole}
                onChange={(e) => setCreateRole(e.target.value)}
              >
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button className="px-3 py-1.5 text-sm border border-border rounded-ui bg-white hover:bg-bg-secondary"
                onClick={() => setCreateOpen(false)}>取消</button>
              <button className="px-3 py-1.5 text-sm text-white bg-text-primary hover:bg-[#1b1f23] rounded-ui border-0 cursor-pointer"
                onClick={handleCreate}>创建</button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}

function MemberTypeSelect({ currentMemberType, onChange }) {
  return (
    <select
      className={`text-xs px-2 py-1 border rounded-sm bg-white cursor-pointer ${
        currentMemberType === "ACTIVE_TEAM"
          ? "border-[#9be9a8] text-[#216e39]"
          : "border-border text-text-secondary"
      }`}
      value={currentMemberType}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="REGULAR">普通成员</option>
      <option value="ACTIVE_TEAM">现役队员</option>
    </select>
  );
}

function RoleSelect({ currentRole, roles, onChange }) {
  return (
    <select
      className="text-xs px-2 py-1 border border-border rounded-sm bg-white text-text-primary cursor-pointer"
      value={currentRole}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="USER">USER</option>
      <option value="ADMIN">ADMIN</option>
      <option value="SUPER_ADMIN">SUPER_ADMIN</option>
    </select>
  );
}
