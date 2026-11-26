export async function performVersionAction<B, R>(
  sourceVersion: number,
  loadBaseline: () => Promise<B>,
  confirmAction: (baseline: B, sourceVersion: number) => boolean,
  perform: (sourceVersion: number, baseline: B) => Promise<R> | R,
): Promise<R | undefined> {
  const baseline = await loadBaseline()
  if (!confirmAction(baseline, sourceVersion)) return undefined
  return perform(sourceVersion, baseline)
}
