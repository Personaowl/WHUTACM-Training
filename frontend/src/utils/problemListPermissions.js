export function visibleProblemLists(lists, sharedOnly = false) {
  return (lists || []).filter((list) => sharedOnly ? list.shared : list.owner);
}

export function canEditProblem(list, problem, userId, isAdmin = false) {
  if (!list || !problem || userId == null) return false;
  if (list.owner) return true;
  if (!list.shared) return false;
  return isAdmin || (problem.addedBy != null && String(problem.addedBy) === String(userId));
}
