export function getCurrentUserPoints(overview, userId) {
  if (userId == null || overview?.currentUserId == null
      || String(overview.currentUserId) !== String(userId)
      || overview.currentUserPoints == null) return null;
  const points = Number(overview.currentUserPoints);
  return Number.isFinite(points) ? points : null;
}
