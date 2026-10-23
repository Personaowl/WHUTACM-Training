export function visibleProblemLists(lists, sharedOnly = false) {
  return (lists || []).filter((list) => sharedOnly ? list.shared : list.owner && !list.collaborative);
}

export function canEditProblem(list, problem, userId, isAdmin = false) {
  if (!list || !problem || userId == null) return false;
  if (list.owner && !list.collaborative) return true;
  if (!list.shared) return false;
  return Boolean(list.collaborative) && (isAdmin || (problem.addedBy != null && String(problem.addedBy) === String(userId)));
}

export function canAddProblem(list) {
  return Boolean(list && (list.collaborative || list.owner));
}
