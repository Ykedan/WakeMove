import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("publishes a valid Android update manifest", async () => {
  const manifest = JSON.parse(
    await readFile(new URL("../public/update.json", import.meta.url), "utf8"),
  );

  assert.equal(manifest.schemaVersion, 1);
  assert.ok(Number.isInteger(manifest.versionCode) && manifest.versionCode > 0);
  assert.match(manifest.versionName, /^\d+\.\d+\.\d+$/);
  assert.match(manifest.releaseUrl, /^https:\/\/github\.com\/Ykedan\/WakeMove\/releases\/tag\//);
  assert.match(manifest.downloadUrl, /^https:\/\/github\.com\/Ykedan\/WakeMove\/releases\/download\//);
  assert.match(
    manifest.fallbackDownloadUrl,
    /^https:\/\/ykedan\.github\.io\/WakeMove\/downloads\/WakeMove-v\d+\.\d+\.\d+\.apk$/,
  );
  assert.ok(manifest.downloadUrl.includes(`v${manifest.versionName}`));
  assert.match(manifest.sha256, /^[a-f0-9]{64}$/);
  assert.ok(manifest.releaseNotes.length > 10);
  assert.ok(manifest.releaseNotesEn.length > 10);
});
