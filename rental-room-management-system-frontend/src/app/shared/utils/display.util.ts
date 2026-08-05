/** The "roomCode — branchName" composite label shown wherever a room needs its branch alongside it. */
export function roomBranchLabel(roomCode: string, branchName: string): string {
  return `${roomCode} — ${branchName}`;
}

/** Renders `value`, or `fallback` (an em dash by default) when it's null/undefined. */
export function displayOr(
  value: string | number | null | undefined,
  fallback = '—',
): string | number {
  return value ?? fallback;
}
