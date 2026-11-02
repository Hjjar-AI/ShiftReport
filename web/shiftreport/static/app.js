/* Only cosmetic preferences persist. Clinical/session state stays on the server. */
(() => {
  "use strict";
  const root = document.documentElement;
  const choices = {
    appearance: ["system", "light", "dark", "amoled"],
    accent: ["teal", "blue", "rose", "amber"],
    "text-size": ["100", "115", "130"],
  };
  for (const [name, allowed] of Object.entries(choices)) {
    const control = document.getElementById(name);
    if (!control) continue;
    let saved;
    try { saved = localStorage.getItem(`shiftreport.web.demo.${name}`); } catch (_) { /* Optional cosmetic storage. */ }
    if (allowed.includes(saved)) control.value = saved;
    const apply = () => {
      root.setAttribute(`data-${name}`, control.value);
      try { localStorage.setItem(`shiftreport.web.demo.${name}`, control.value); } catch (_) { /* Keep in memory. */ }
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
    patients.forEach(patient => patient.addEventListener("toggle", updateLabel));
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
    let timer, submitting = false, pending = Promise.resolve();
    const status = editor.querySelector("[data-draft-status]");
    const saveDraft = () => {
      if (submitting) return;
      const data = new FormData(editor);
      data.set("action", "draft");
      pending = pending.catch(() => {}).then(async () => {
        if (submitting) return;
        try {
          const result = await fetch(editor.action || location.href, {method:"POST", body:data, credentials:"same-origin"});
          status.textContent = result.status === 204 ? "حُفظت مسودة الإدخال • الحفظ السريري يحتاج زر حفظ" : "لم تُحفظ المسودة؛ أبقِ الصفحة مفتوحة وراجع الهوية أو الحفظ";
        } catch (_) { status.textContent = "تعذّر حفظ المسودة؛ أبقِ الصفحة مفتوحة"; }
      });
    };
    const changed = () => { clearTimeout(timer); timer = setTimeout(saveDraft, 600); };
    editor.addEventListener("input", changed);
    editor.addEventListener("change", changed);
    for (const section of editor.querySelectorAll("[data-repeat]")) {
      const rows = section.querySelector("[data-rows]");
      const setup = row => {
        const hidden = row.querySelector('input[name="task_id"], input[name="lab_id"]');
        if (hidden && !hidden.value) hidden.value = crypto.randomUUID();
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
      if (submitting) return;
      event.preventDefault();
      const button = event.submitter;
      clearTimeout(timer); submitting = true;
      pending.finally(() => {
        if (button) {
          const field = document.createElement("input"); field.type = "hidden"; field.name = button.name; field.value = button.value; editor.append(field);
        }
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
