const PROFILE_FIELDS = ["username", "email", "displayName", "realName", "college", "className", "bio"];

export function createProfileDraft(profile) {
  return Object.fromEntries([...PROFILE_FIELDS.map((field) => [field, profile?.[field] ?? ""]), ["password", ""]]);
}

export function createProfileUpdate(draft) {
  // Empty strings explicitly clear optional values; null means "leave unchanged" to the API.
  const payload = Object.fromEntries(PROFILE_FIELDS.map((field) => [field, String(draft[field] ?? "").trim()]));
  if (draft.password?.trim()) payload.password = draft.password;
  return payload;
}
