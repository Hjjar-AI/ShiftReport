/* Only cosmetic preferences persist. Clinical/session state stays on the server. */
(() => {
  "use strict";
  const root = document.documentElement;
  const choices = {
    appearance: ["system", "light", "dark", "amoled"],
    accent: ["teal", "blue", "rose", "amber"],
    "text-size": ["100", "115", "130"],
    motion: ["system", "reduce"],
  };
  for (const [name, allowed] of Object.entries(choices)) {
    const control = document.getElementById(name);
    if (!control) continue;
    const saved = root.getAttribute(`data-${name}`);
    if (allowed.includes(saved)) control.value = saved;
    const apply = () => {
      root.setAttribute(`data-${name}`, control.value);
      if (["appearance", "accent"].includes(name)) {
        try { localStorage.setItem(`shiftreport.web.${root.dataset.workspace}.${name}`, control.value); } catch (_) { /* Keep in memory. */ }
      }
    };
    apply();
    control.addEventListener("change", apply);
  }
  const spacing = document.getElementById("spacing");
  if (spacing) spacing.addEventListener("change", () => { root.dataset.spacing = spacing.value; });

  const expand = document.querySelector("[data-expand-all]");
  if (expand) {
    const patients = [...document.querySelectorAll("details.patient")];
    const updateLabel = () => {
      const allOpen = patients.length > 0 && patients.every(patient => patient.open);
      expand.textContent = allOpen ? "طي الكل" : "توسيع الكل";
      expand.disabled = patients.length === 0;
    };
    expand.addEventListener("click", () => {
      const desired = !patients.every(patient => patient.open);
      patients.forEach(patient => { patient.open = desired; });
      updateLabel();
    });
    const reduceMotion = () => root.dataset.motion === "reduce" || matchMedia("(prefers-reduced-motion: reduce)").matches;
    for (const patient of patients) {
      patient.querySelector("summary").addEventListener("click", () => {
        if (!patient.open && !reduceMotion()) {
          const content = patient.querySelector(".clinical-grid");
          requestAnimationFrame(() => content.animate([{opacity:0},{opacity:1}],{duration:120}));
        }
      });
      patient.addEventListener("toggle", updateLabel);
    }
    updateLabel();
  }
  for (const form of document.querySelectorAll("form[data-confirm]")) {
    form.addEventListener("submit", event => {
      if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    });
  }
  function revealPatient() {
    const target = document.getElementById(window.location.hash.slice(1));
    if (target && target.matches("details.patient")) {
      target.open = true;
      target.scrollIntoView({ block: "start" });
    }
  }
  revealPatient();
  window.addEventListener("hashchange", revealPatient);
})();

(() => {
  "use strict";
  const editor = document.querySelector("[data-patient-editor]");
  if (editor) {
    let timer, submitting = false, pending = Promise.resolve(), dirty = false;
    const status = editor.querySelector("[data-draft-status]");
    const saveDraft = () => {
      if (submitting) return;
      dirty = false;
      const data = new FormData(editor);
      data.set("action", "draft");
      pending = pending.catch(() => {}).then(async () => {
        try {
          const result = await fetch(editor.getAttribute("action") || location.href, {method:"POST", body:data, credentials:"same-origin"});
          status.textContent = result.status === 204 ? "حُفظت مسودة الإدخال • الحفظ السريري يحتاج زر حفظ" : "لم تُحفظ المسودة؛ أبقِ الصفحة مفتوحة وراجع الهوية أو الحفظ";
          return result.status === 204;
        } catch (_) { status.textContent = "تعذّر حفظ المسودة؛ أبقِ الصفحة مفتوحة"; return false; }
      });
    };
    // Host interruptions use this same queue/form owner, never a second writer.
    editor.addEventListener("shiftreport:flush-draft", event => {
      if (typeof event.detail?.waitUntil !== "function") return;
      clearTimeout(timer);
      if (dirty && !submitting) saveDraft();
      event.detail.waitUntil(submitting ? Promise.resolve(false) : pending);
    });
    const changed = () => { dirty = true; clearTimeout(timer); timer = setTimeout(saveDraft, 600); };
    editor.addEventListener("input", changed);
    editor.addEventListener("change", changed);
    for (const section of editor.querySelectorAll("[data-repeat]")) {
      const rows = section.querySelector("[data-rows]");
      const setup = row => {
        const hidden = row.querySelector('input[name="task_id"], input[name="lab_id"]');
        if (hidden && !hidden.value) hidden.value = window.shiftreportIphone ? window.shiftreportIphone.uuid() : crypto.randomUUID();
        const done = row.querySelector('input[name="task_done"]');
        if (done) done.value = hidden.value;
        row.querySelector("[data-remove]").addEventListener("click", () => {
          if (confirm("حذف هذا العنصر من المسودة؟")) { row.remove(); changed(); }
        });
        for (const button of row.querySelectorAll("[data-move]")) button.addEventListener("click", () => {
          const delta = Number(button.dataset.move);
          if (delta === -1 && row.previousElementSibling) rows.insertBefore(row, row.previousElementSibling);
          if (delta === 1 && row.nextElementSibling) rows.insertBefore(row.nextElementSibling, row);
          changed();
        });
      };
      rows.querySelectorAll("[data-row]").forEach(setup);
      section.querySelector("[data-add]").addEventListener("click", () => {
        const row = section.querySelector("template").content.firstElementChild.cloneNode(true);
        setup(row); rows.append(row); row.querySelector("input:not([type=hidden]),textarea")?.focus(); changed();
      });
    }
    editor.addEventListener("submit", event => {
      if (submitting) { event.preventDefault(); return; }
      event.preventDefault();
      const button = event.submitter;
      clearTimeout(timer);
      if (dirty) saveDraft();
      submitting = true;
      editor.querySelectorAll("button[type=submit],button[name=action]").forEach(control => { control.disabled=true; });
      pending.finally(() => {
        if (button) {
          const field = document.createElement("input"); field.type = "hidden"; field.name = button.name; field.value = button.value; editor.append(field);
        }
        window.shiftreportIphone?.operationStarted();
        HTMLFormElement.prototype.submit.call(editor);
      });
    });
  }
  const reportAccent = document.querySelector("[data-report-accent]");
  const accent = document.getElementById("accent");
  if (reportAccent && accent) {
    const apply = () => { reportAccent.value = ({teal:"VANILLA",blue:"SYSTEM",rose:"COASTAL",amber:"CREAM"})[accent.value] || "VANILLA"; };
    apply(); accent.addEventListener("change", apply);
  }
})();

(() => {
  const accent = document.getElementById("accent");
  const csrf = document.querySelector('meta[name="csrf-token"]')?.content;
  if (accent && csrf) {
    const sync = async () => {
      const body = new FormData(); body.set("csrf", csrf); body.set("accent", accent.value);
      body.set("text_size", document.getElementById("text-size").value);
      body.set("motion", document.getElementById("motion").value);
      try { await fetch("/appearance", {method:"POST",body,credentials:"same-origin"}); } catch (_) { /* A future preview captures the selected color again. */ }
    };
    sync();
    for (const id of ["accent","text-size","motion"]) document.getElementById(id)?.addEventListener("change", sync);
  }
})();

(() => {
  const copy = document.querySelector("[data-copy-patient]");
  if (copy) {
    const patient = document.querySelector("details.patient");
    if (patient) patient.open = true;
    copy.addEventListener("click", async () => {
      const content = [...patient.querySelectorAll("summary,.clinical-grid section")].map(node => node.innerText).join("\n\n");
      try { await navigator.clipboard.writeText(content); copy.textContent = "نُسخ النص"; }
      catch (_) { copy.textContent = "حدّد النص وانسخه من المتصفح"; }
    });
  }
})();

(() => {
  const seconds = Number(document.body.dataset.lockSeconds);
  const csrf = document.querySelector('meta[name="csrf-token"]')?.content;
  if (!seconds || !csrf) return;
  let timer, lastTouch = 0;
  const channel = typeof BroadcastChannel === "function" ? new BroadcastChannel(`shiftreport.web.activity.${csrf.slice(0,16)}`) : null;
  const lock = async () => {
    document.querySelector(".app-shell").hidden = true;
    channel?.postMessage("locked");
    const body = new FormData(); body.set("csrf", csrf); body.set("action", "lock");
    try { await fetch("/identity", {method:"POST",body,credentials:"same-origin"}); }
    finally { location.replace("/identity"); }
  };
  const reset = () => { clearTimeout(timer); timer = setTimeout(lock, seconds * 1000); };
  const active = () => {
    reset(); channel?.postMessage("active");
    if (Date.now() - lastTouch < 30000) return;
    lastTouch = Date.now();
    const body = new FormData(); body.set("csrf", csrf);
    fetch("/session/touch", {method:"POST",body,credentials:"same-origin"}).then(result => {
      if (result.status !== 204) location.replace("/identity");
    }).catch(() => {});
  };
  for (const name of ["pointerdown", "keydown", "wheel"]) document.addEventListener(name, active, {passive:true});
  if (channel) channel.onmessage = event => {
    if (event.data === "locked") { document.querySelector(".app-shell").hidden = true; location.replace("/identity"); }
    else reset();
  };
  reset();
})();


(() => {
  for (const button of document.querySelectorAll("[data-share-artifact]")) {
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        const response = await fetch(button.dataset.shareArtifact, {credentials:"same-origin"});
        if (!response.ok || !/^(application\/pdf|text\/plain)/.test(response.headers.get("Content-Type") || "")) throw Error();
        const blob = await response.blob();
        const file = new File([blob],button.dataset.filename,{type:blob.type});
        if (navigator.canShare && navigator.canShare({files:[file]}) && navigator.userActivation?.isActive) {
          await navigator.share({files:[file]});
        } else {
          const url = URL.createObjectURL(file);
          const link = document.createElement("a"); link.href = url; link.download = file.name;
          link.click(); setTimeout(() => URL.revokeObjectURL(url), 30000);
          button.textContent = "حُفظ الملف للمشاركة من جهازك";
        }
      } catch (error) {
        if (error.name !== "AbortError") button.textContent = "تعذّر تجهيز الملف؛ افتح المعاينة وحدّث المراجعة";
      } finally { button.disabled = false; }

    });
  }
})();

(() => {
  const intro = document.querySelector("[data-intro]");
  if (!intro) return;
  intro.dataset.active="1";
  const button = intro.querySelector("button");
  let held = false;
  // A fixed deadline keeps running while held; an expired hold ends on release.
  const deadline = performance.now() + Number(intro.dataset.remaining);
  const dismiss = () => {
    intro.remove();
    const body=new FormData(); body.set("csrf",document.querySelector('meta[name="csrf-token"]').content);
    fetch("/intro/dismiss",{method:"POST",body,credentials:"same-origin"}).catch(() => {});
  };
  button.addEventListener("pointerdown", event => {
    held = true; button.setPointerCapture(event.pointerId);
  });
  const release = () => { held = false; if (performance.now() >= deadline) dismiss(); };
  button.addEventListener("pointerup", release);
  button.addEventListener("pointercancel", release);
  button.addEventListener("click", dismiss);
  document.addEventListener("keydown", event => { if (event.key === "Escape") dismiss(); });
  setTimeout(() => { if (!held) dismiss(); }, Math.max(0,deadline-performance.now()));
})();
