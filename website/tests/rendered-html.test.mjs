import assert from "node:assert/strict";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("renders the WakeMove product page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /WakeMove 醒动/);
  assert.match(html, /叫醒你的/);
  assert.match(html, /下载 Android APK/);
  assert.match(html, /WakeMove-v1\.4\.0\.apk/);
  assert.match(html, /%2Fscreens%2Fhome\.png/);
  assert.doesNotMatch(html, /Your site is taking shape|react-loading-skeleton/);
});
