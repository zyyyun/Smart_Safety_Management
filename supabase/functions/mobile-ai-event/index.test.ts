import { buildCapturePath, normalizeAccuracy } from "./helpers.ts";

Deno.test("buildCapturePath uses detection bucket prefix convention", () => {
  assertEquals(
    buildCapturePath(5, 1780298670271),
    "detection/5/fire_5_1780298670271.jpg",
  );
});

Deno.test("normalizeAccuracy clamps to zero through one", () => {
  assertEquals(normalizeAccuracy(-1), 0);
  assertEquals(normalizeAccuracy(0.75), 0.75);
  assertEquals(normalizeAccuracy(2), 1);
});

function assertEquals(actual: unknown, expected: unknown) {
  if (actual !== expected) {
    throw new Error(`Expected ${String(expected)}, got ${String(actual)}`);
  }
}
