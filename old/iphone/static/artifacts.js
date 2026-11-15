/* Status polling is read-only and bounded. Never replay a native file request. */
(() => {
  "use strict";
  const panel = document.querySelector("[data-native-file-operation]");
  if (!panel) return;
  let remaining = 30, polling = false;
  const poll = async () => {
    if (polling || remaining <= 0 || document.hidden || !["queued","running"].includes(panel.dataset.state)) return;
    remaining -= 1; polling = true;
    const controller = typeof AbortController === "function" ? new AbortController() : null;
    let timer;
    try {
      const request = fetch(panel.dataset.statusUrl, {credentials:"same-origin",cache:"no-store",redirect:"error",
        ...(controller ? {signal:controller.signal} : {})}).then(response => response.ok ? response.json() : null);
      const result = await Promise.race([request, new Promise((resolve,reject) => {
        timer = setTimeout(() => { controller?.abort(); reject(new Error("timeout")); }, 4000);
      })]);
      if (!result) return;
      panel.dataset.state = result.state;
      panel.querySelector("[data-native-file-status]").textContent = result.message;
      panel.querySelector("[data-native-file-review]").hidden = result.state !== "review";
      panel.querySelector("[data-native-file-closed]").hidden = !["share_requested","uncertain","failed_open"].includes(result.state);
      panel.querySelector("[data-native-file-cancel]").hidden = result.state !== "queued";
    } catch (_) { /* The explicit status link remains available. */ }
    finally { clearTimeout(timer); polling = false; }
    setTimeout(poll, 1000);
  };
  setTimeout(poll, 1000);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) void poll(); });
})();
