import assert from "node:assert/strict";
import { test } from "node:test";
import { createProfileDraft, createProfileUpdate } from "../src/utils/profileForm.js";

test("old accounts open with empty optional fields and never prefill a password", () => {
  const draft = createProfileDraft({ username: "member", password: "must-not-be-copied" });
  assert.equal(draft.username, "member");
  for (const field of ["realName", "college", "className", "bio", "email", "password"]) {
    assert.equal(draft[field], "");
  }
});

test("empty optional fields explicitly clear saved information without changing the password", () => {
  const payload = createProfileUpdate(createProfileDraft({ username: "member" }));
  for (const field of ["realName", "college", "className", "bio", "email", "displayName"]) {
    assert.equal(payload[field], "");
  }
  assert.equal(Object.hasOwn(payload, "password"), false);
});

test("school information is trimmed and a new password is only sent when supplied", () => {
  const payload = createProfileUpdate({
    ...createProfileDraft({ username: "member" }),
    realName: " 张三 ", college: " 计算机学院 ", className: " 计科2401 ", password: "new-password",
  });
  assert.equal(payload.realName, "张三");
  assert.equal(payload.college, "计算机学院");
  assert.equal(payload.className, "计科2401");
  assert.equal(payload.password, "new-password");
});
