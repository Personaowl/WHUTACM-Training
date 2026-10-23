import test from "node:test";
import assert from "node:assert/strict";
import { canEditProblem, visibleProblemLists } from "../src/utils/problemListPermissions.js";

test("personal and shared pages select their own lists", () => {
  const lists = [{ owner: true, shared: false }, { owner: false, shared: true }, { owner: true, shared: true }];
  assert.equal(visibleProblemLists(lists).length, 2);
  assert.equal(visibleProblemLists(lists, true).length, 2);
});

test("shared contributors edit only their own problems; owner and admin moderate", () => {
  const list = { owner: false, shared: true, collaborative: true };
  assert.equal(canEditProblem(list, { addedBy: 2 }, 2), true);
  assert.equal(canEditProblem(list, { addedBy: 3 }, 2), false);
  assert.equal(canEditProblem(list, { addedBy: 3 }, 2, true), true);
  assert.equal(canEditProblem({ owner: true }, { addedBy: 3 }, 2), true);
  assert.equal(canEditProblem({ owner: false, shared: false }, { addedBy: 2 }, 2, true), false);
  assert.equal(canEditProblem(list, { addedBy: null }, 2), false);
  assert.equal(canEditProblem(list, { addedBy: 2 }, null), false);
});

test("published lists stay read-only even for admins and past contributors", () => {
  const list = { owner: false, shared: true, collaborative: false };
  assert.equal(canEditProblem(list, { addedBy: 2 }, 2, true), false);
  assert.equal(canEditProblem(list, { addedBy: 2 }, 2), false);
});
