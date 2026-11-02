/* No clinical data/credentials enter browser storage. Native commands are not accepted. */
(() => {
  "use strict";
  const root = document.documentElement;
  let state = "covered", epoch = 0, checking = null, operation = false, priorFocus = null;
  const status = () => document.querySelector("[data-iphone-status]");
  const setStatus = message => { if (status()) status().textContent = message; };
  const sidebar = () => document.querySelector(".sidebar");
  const focusable = node => [...node.querySelectorAll("a[href],button,input,select,textarea,summary,[tabindex]")]
    .filter(item => !item.disabled && item.tabIndex >= 0 && item.getClientRects().length);

  function menu(open) {
    const drawer = sidebar();
    if (!drawer) return;
    root.dataset.iphoneMenu = open ? "open" : "closed";
    document.querySelector("[data-iphone-menu-toggle]")?.setAttribute("aria-expanded", String(open));
    document.querySelector("[data-iphone-menu-close]").hidden = !open;
    if (open) {
      priorFocus = document.activeElement;
      drawer.setAttribute("role", "dialog"); drawer.setAttribute("aria-modal", "true");
      document.querySelector("main").setAttribute("aria-hidden", "true");
      focusable(drawer)[0]?.focus();
    } else {
      drawer.removeAttribute("role"); drawer.removeAttribute("aria-modal");
      document.querySelector("main").removeAttribute("aria-hidden");
      if (state === "ready") priorFocus?.focus();
    }
  }

  function flushDraft() {
    const editor = document.querySelector("[data-patient-editor]");
    if (!editor) return Promise.resolve(true);
    let completion = Promise.resolve(false);
    editor.dispatchEvent(new CustomEvent("shiftreport:flush-draft", {
      detail: {waitUntil: promise => { completion = promise; }},
    }));
    return completion; // The shared editor remains the only autosave owner.
  }

  function cover() {
    epoch += 1;
    state = "covered"; root.dataset.iphoneState = "covered";
    document.activeElement?.blur();
    menu(false);
    setStatus("المحتوى مخفي. يلزم التحقق من الجلسة قبل إظهاره.");
    void flushDraft(); // Opportunistic; iOS can suspend before completion.
  }

  async function resume(nativeActive = false) {
    if (document.hidden && !nativeActive) return false;
    if (checking?.epoch === epoch) return checking.promise;
    const captured = ++epoch;
    state = "checking"; root.dataset.iphoneState = "covered";
    setStatus("جارٍ التحقق من الجلسة المحلية…");
    const controller = typeof AbortController === "function" ? new AbortController() : null;
    let timer;
    const request = fetch("/_iphone/session", {
      credentials: "same-origin", cache: "no-store", redirect: "error",
      headers: {"X-ShiftReport-Page": document.querySelector('meta[name="iphone-page-identity"]')?.content || ""},
      ...(controller ? {signal: controller.signal} : {}),
    }).then(response => response.ok ? response.json() : null);
    const task = (async () => {
      try {
        const result = await Promise.race([request, new Promise((resolve, reject) => {
          timer = setTimeout(() => { controller?.abort(); reject(new Error("timeout")); }, 4000);
        })]);
        if (captured !== epoch || (document.hidden && !nativeActive)) return false;
        if (!result?.ready) throw new Error("session");
        state = "ready";
        root.dataset.iphoneState = operation ? "uncertain" : "ready";
        const leave = document.querySelector("[data-iphone-leave]");
        if (leave) leave.hidden = !operation;
        if (operation) setStatus("عاد الاتصال؛ هذا لا يؤكد نتيجة العملية السابقة. راجع البيانات الحالية قبل اتخاذ أي إجراء جديد. لم تُعد العملية تلقائياً.");
        return true;
      } catch (_) {
        if (captured === epoch) {
          state = "lost";
          setStatus("تعذّر تأكيد الجلسة أو انتهت. أبقِ العرض مفتوحاً؛ لم تبدأ إعادة حفظ أو إرسال. استخدم التحقق أو عد إلى المشغّل.");
        }
        return false;
      } finally {
        clearTimeout(timer);
        if (checking?.epoch === captured) checking = null;
      }
    })();
    checking = {epoch: captured, promise: task};
    return task;
  }

  function uuid() {
    const source = window.crypto;
    if (typeof source?.randomUUID === "function") return source.randomUUID();
    if (typeof source?.getRandomValues !== "function") throw new Error("Secure browser randomness is unavailable");
    const bytes = source.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
    const hex = [...bytes].map(value => value.toString(16).padStart(2, "0")).join("");
    return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join("-");
  }

  function operationStarted() {
    operation = true;
    root.dataset.iphoneOperation = "busy";
    root.dataset.iphoneState = "busy";
    setStatus("جارٍ تنفيذ العملية المحلية. انقطاع الاتصال لا يؤكد نتيجتها؛ لن تُعد تلقائياً.");
    const indicator = document.querySelector(".iphone-operation");
    if (indicator) indicator.hidden = false;
  }

  window.shiftreportIphone = Object.freeze({cover, resume, uuid, operationStarted, get state() { return state; }});

  document.addEventListener("DOMContentLoaded", () => {
    const drawer = sidebar();
    if (drawer) {
      drawer.id = "iphone-navigation";
      const close = document.createElement("button");
      close.type = "button"; close.className = "secondary iphone-menu-close";
      close.textContent = "إغلاق القائمة"; close.addEventListener("click", () => menu(false));
      drawer.prepend(close);
    }
    document.querySelector("[data-iphone-menu-toggle]")?.addEventListener("click", () => menu(true));
    document.querySelector("[data-iphone-menu-close]")?.addEventListener("click", () => menu(false));
    document.querySelector("[data-iphone-resume]")?.addEventListener("click", () => { void resume(); });
    document.addEventListener("keydown", event => {
      const trap = root.dataset.iphoneMenu === "open" ? drawer : root.dataset.iphoneState !== "ready" ? document.querySelector(".iphone-cover") : null;
      if (event.key === "Escape" && root.dataset.iphoneMenu === "open") { event.preventDefault(); menu(false); }
      if (event.key === "Tab" && trap) {
        const items = focusable(trap), first = items[0], last = items[items.length-1];
        if (!items.includes(document.activeElement) || (!event.shiftKey && document.activeElement === last)) { event.preventDefault(); first?.focus(); }
        else if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      }
    });
    document.addEventListener("focusin", event => {
      if (root.dataset.iphoneMenu === "open" && !drawer.contains(event.target)) focusable(drawer)[0]?.focus();
      else if (root.dataset.iphoneState !== "ready" && !event.target.closest(".iphone-cover")) focusable(document.querySelector(".iphone-cover"))[0]?.focus();
    });
    document.addEventListener("submit", event => {
      if (operation) { event.preventDefault(); event.stopImmediatePropagation(); }
    }, true);
    document.addEventListener("submit", event => {
      if (!event.defaultPrevented && event.target.method?.toLowerCase() === "post") operationStarted();
    });
    const footer = document.querySelector(".patient-editor .editor-actions");
    function viewport() {
      const visual = window.visualViewport;
      const bottom = visual ? Math.max(0, window.innerHeight - visual.height - visual.offsetTop) : 0;
      root.style.setProperty("--iphone-keyboard", bottom + "px");
      if (footer) root.style.setProperty("--iphone-footer", Math.ceil(footer.getBoundingClientRect().height) + "px");
    }
    window.visualViewport?.addEventListener("resize", viewport);
    window.visualViewport?.addEventListener("scroll", viewport);
    window.addEventListener("resize", viewport);
    if (footer && typeof ResizeObserver === "function") new ResizeObserver(viewport).observe(footer);
    viewport();
  });
  document.addEventListener("visibilitychange", () => { if (document.hidden) cover(); else void resume(); });
  window.addEventListener("pagehide", cover);
  window.addEventListener("pageshow", event => { if (event.persisted) { cover(); void resume(); } });
})();
