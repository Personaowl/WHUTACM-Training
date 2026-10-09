import * as Avatar from "@radix-ui/react-avatar";
import { getUserInitial } from "../../auth";

export default function UserAvatar({ user, size = 40 }) {
  const initial = getUserInitial(user);
  const rawUrl = typeof user?.avatarUrl === "string" ? user.avatarUrl.trim() : "";
  const avatarUrl = /^data:image\/(png|jpeg|webp);base64,/i.test(rawUrl) ? rawUrl : "";
  const name = user?.displayName || user?.username || "用户";

  return (
    <Avatar.Root
      className="rounded-full overflow-hidden flex-shrink-0 inline-flex"
      style={{ width: size, height: size }}
    >
      {avatarUrl && (
        <Avatar.Image
          key={avatarUrl}
          src={avatarUrl}
          alt={`${name}的头像`}
          referrerPolicy="no-referrer"
          className="w-full h-full object-cover"
        />
      )}
      <Avatar.Fallback
        className="w-full h-full bg-text-primary text-white flex items-center justify-center font-semibold"
        style={{ fontSize: size * 0.4 }}
        role="img"
        aria-label={`${name}的默认头像`}
      >
        {initial}
      </Avatar.Fallback>
    </Avatar.Root>
  );
}
