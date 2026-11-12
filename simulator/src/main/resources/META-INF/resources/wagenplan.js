"use strict";

/* Zeichnet den Wagenplan als SVG; gemeinsam genutzt von der Reservierungs- und der Szenario-Seite.
   Der Server liefert die fertig gezeichnete Draufsicht des Wagens (Formen mit Pfaden und Stilklassen); der Client baut sie einmal je Wagen
   auf und überträgt danach nur noch Zustand und Auswahl der Plätze als Stilklassen. */
const Wagenplan = (() => {
  const NS = "http://www.w3.org/2000/svg";
  const ansichten = new Map();
  let piktogramme = null;

  const bezeichnung = { FREI: "verfügbar", BELEGT: "belegt", VORGEMERKT: "von anderer Anfrage vorgemerkt", GRUPPENKONTINGENT: "für Gruppen reserviert", SONDERPLATZ: "Sonderplatz, nicht angefordert", ANDERE_ZONE: "andere Zone" };
  const zonen = { RUHE: "Ruhebereich", KINDER: "Kinderbereich" };
  const zustaende = Object.keys(bezeichnung).map((z) => "zustand-" + z.toLowerCase().replace(/_/g, "-"));

  async function abrufen(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error("Fehler " + res.status);
    return res.json();
  }

  function neu(tag, attribute, eltern) {
    const e = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attribute)) e.setAttribute(k, v);
    return eltern ? eltern.appendChild(e) : e;
  }

  /* Baut die Draufsicht des Wagens im SVG auf; mit ebenenWahl (select) lassen sich die Ebenen eines Doppelstockwagens umschalten */
  async function vorbereiten(svg, wagenId, ebenenWahl) {
    if (!piktogramme) piktogramme = await abrufen("/api/v1/wagenlayouts/piktogramme");
    if (!ansichten.has(wagenId)) ansichten.set(wagenId, await abrufen(`/api/v1/wagen/${encodeURIComponent(wagenId)}/ansicht`));
    const ansicht = ansichten.get(wagenId);

    svg.replaceChildren();
    svg.setAttribute("viewBox", `-.35 -.35 ${ansicht.breiteM + .7} ${ansicht.hoeheM + .7}`);
    svg.dataset.wagen = wagenId;
    svg._plaetze = new Map();
    for (const f of ansicht.formen) {
      const g = neu("g", { class: f.stile.join(" "), "data-id": f.id, "data-ebene": f.ebene ?? "" }, svg);
      if (f.pfad) neu("path", { d: f.pfad, "fill-rule": "evenodd" }, g);
      if (f.piktogramm) {
        const p = f.piktogramm;
        neu("g", { class: "piktogramm", transform: `translate(${p.position.x - p.groesse / 2} ${p.position.y - p.groesse / 2}) scale(${p.groesse / 24})` }, g)
          .innerHTML = piktogramme[p.art];
      }
      if (f.beschriftung) {
        const b = f.beschriftung;
        neu("text", { x: b.position.x, y: b.position.y, "font-size": b.groesse, "text-anchor": "middle" }, g).textContent = b.text;
      }
      if (f.stile.includes("platz")) svg._plaetze.set(f.id, { g, lehne: null });
      else if (f.stile.includes("platz-lehne")) {
        const eintrag = svg._plaetze.get(f.id.replace(/\/lehne$/, ""));
        if (eintrag) eintrag.lehne = g;
      }
    }

    const ebenen = ansicht.ebenen;
    if (ebenenWahl) {
      ebenenWahl.replaceChildren(...ebenen.map((e) => new Option(e, e)));
      for (const l of ebenenWahl.labels) l.hidden = ebenen.length < 2;
      ebenenWahl.hidden = ebenen.length < 2;
      ebenenWahl.onchange = () => ebeneZeigen(svg, ebenenWahl.value);
    }
    ebeneZeigen(svg, ebenen[0]);
  }

  function ebeneZeigen(svg, ebene) {
    for (const g of svg.querySelectorAll("[data-ebene]"))
      g.classList.toggle("eb-aus", !!g.dataset.ebene && g.dataset.ebene !== ebene);
  }

  /* Überträgt Zustand und Auswahl der Plätze auf die Draufsicht; vorbereiten() muss für den Wagen des Plans abgeschlossen sein */
  function zeichnen(svg, plan, opts = {}) {
    if (svg.dataset.wagen !== plan.wagenId) return;
    const gewaehlt = opts.gewaehlt || new Set();
    const neuGebucht = opts.neu || new Set();

    for (const s of plan.sitzplaetze) {
      const eintrag = svg._plaetze.get(s.id);
      if (!eintrag) continue;
      const zustand = "zustand-" + s.zustand.toLowerCase().replace(/_/g, "-");
      const name = s.art === "ROLLSTUHL" ? "Rollstuhlplatz" : s.art === "FAHRRAD" ? "Fahrradstellplatz" : "Sitz " + s.bezeichnung;
      const text = `${name}, ${gewaehlt.has(s.id) ? "gewählt" : neuGebucht.has(s.id) ? "neu reserviert" : bezeichnung[s.zustand]}`;
      for (const g of [eintrag.g, eintrag.lehne]) {
        if (!g) continue;
        g.classList.remove(...zustaende);
        g.classList.add(zustand);
        g.classList.toggle("gewaehlt", gewaehlt.has(s.id));
        g.classList.toggle("neu", neuGebucht.has(s.id));
      }
      const g = eintrag.g;
      g.setAttribute("role", "button");
      g.setAttribute("tabindex", s.zustand === "FREI" ? "0" : "-1");
      g.setAttribute("aria-label", text);
      g.querySelector("title")?.remove();
      const titel = neu("title", {});
      titel.textContent = text + (s.art === "SITZ" && zonen[s.zone] ? ", " + zonen[s.zone] : "");
      g.prepend(titel);
    }
  }

  /* Kurzname eines Platzes in Texten: die Id ohne das Präfix der Wagen-Id */
  function kurz(wagenId, sitzId) {
    return sitzId.startsWith(wagenId + "-") ? sitzId.slice(wagenId.length + 1) : sitzId;
  }

  return { vorbereiten, zeichnen, kurz };
})();
