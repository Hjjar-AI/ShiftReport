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
