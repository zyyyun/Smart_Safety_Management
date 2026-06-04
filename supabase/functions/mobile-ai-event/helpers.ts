export function buildCapturePath(
  cameraId: number,
  timestampMs: number,
  suffix?: string,
) {
  const suffixPart = suffix ? `_${suffix}` : "";
  return `detection/${cameraId}/fire_${cameraId}_${timestampMs}${suffixPart}.jpg`;
}

export function normalizeAccuracy(value: unknown) {
  if (typeof value !== "number" || Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(1, value));
}
