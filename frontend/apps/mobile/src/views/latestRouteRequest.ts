export async function commitLatestRouteRequest<T>(
  generation: number,
  currentGeneration: () => number,
  request: () => Promise<T>,
  commit: (value: T) => void,
): Promise<boolean> {
  const value = await request();
  if (generation !== currentGeneration()) return false;
  commit(value);
  return true;
}
