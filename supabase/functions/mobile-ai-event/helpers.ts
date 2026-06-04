export function buildCapturePath(cameraId: number, timestampMs: number) {
  return `detection/${cameraId}/fire_${cameraId}_${timestampMs}.jpg`;
}

export function normalizeAccuracy(value: unknown) {
  if (typeof value !== "number" || Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(1, value));
}
