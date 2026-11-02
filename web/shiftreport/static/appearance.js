/* Apply isolated cosmetic choices before the first layout. */
(() => {
  const root = document.documentElement;
  const scope = root.dataset.workspace;
  for (const [name, allowed] of Object.entries({appearance:["system","light","dark","amoled"],accent:["teal","blue","rose","amber"]})) {
    let saved;
    try { saved = localStorage.getItem(`shiftreport.web.${scope}.${name}`); } catch (_) {}
    if (allowed.includes(saved)) root.setAttribute(`data-${name}`, saved);
  }
  // Retire the old unintended persistent text-size value; session choices are server-owned.
  try { localStorage.removeItem("shiftreport.web.demo.text-size"); } catch (_) {}
})();
