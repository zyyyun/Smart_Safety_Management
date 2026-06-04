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

Deno.test("index uses canonical Korean fire event name", async () => {
  const text = await Deno.readTextFile(
    new URL("./index.ts", import.meta.url),
  );

  assertIncludes(text, 'const FIRE_EVENT_NAME = "화재";');
});

function assertEquals(actual: unknown, expected: unknown) {
  if (actual !== expected) {
    throw new Error(`Expected ${String(expected)}, got ${String(actual)}`);
  }
}

function assertIncludes(actual: string, expected: string) {
  if (!actual.includes(expected)) {
    throw new Error(`Expected source to contain ${expected}`);
  }
}
