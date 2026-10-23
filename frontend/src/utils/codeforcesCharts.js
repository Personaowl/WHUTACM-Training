export function buildTagDistribution(items = []) {
  const sorted = [...items].sort((left, right) =>
    right.count - left.count || left.tag.localeCompare(right.tag, "en"));
  const totalTagCount = sorted.reduce((sum, item) => sum + item.count, 0);
  const maximum = sorted[0]?.count || 0;
  return {
    totalTagCount,
    items: sorted.map(item => ({
      ...item,
      widthPercent: maximum ? (item.count / maximum) * 100 : 0,
      sharePercent: totalTagCount ? (item.count / totalTagCount) * 100 : 0,
    })),
  };
}
