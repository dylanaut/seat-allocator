"use strict";

/* Zeichnet den Wagenplan als SVG; gemeinsam genutzt von der Reservierungs- und der Szenario-Seite */
const Wagenplan = (() => {
  const CW = 66, CH = 50, X0 = 46, Y0 = 34, AISLE = 26, SW = CW - 14, SH = CH - 10, TISCH = 24;

  function colY(col) { return Y0 + col * CH + (col >= 2 ? AISLE : 0); }

  /* Sitzreihen links nach rechts; Tischreihenpaare (2k, 2k+1) erhalten Platz für den Tisch dazwischen */
  function layoutX(plan) {
    const hatTisch = new Set(plan.sitzplaetze.filter((s) => s.hatTisch).map((s) => s.reihe));
    const rows = [...new Set(plan.sitzplaetze.map((s) => s.reihe))].sort((a, b) => a - b);
    const xs = new Map();
    let x = X0;
    rows.forEach((r, i) => {
      if (i > 0) {
        const prev = rows[i - 1];
        const paar = hatTisch.has(r) && hatTisch.has(prev) && Math.floor(r / 2) === Math.floor(prev / 2);
        x += CW + (paar ? TISCH : 0);
      }
      xs.set(r, x);
    });
    return { rows, xs, breite: x + CW + 16 };
  }

  function zeichnen(svg, plan, opts = {}) {
    const gewaehlt = opts.gewaehlt || new Set();
    const neu = opts.neu || new Set();
    const { rows, xs, breite } = layoutX(plan);
    const h = Y0 + 4 * CH + AISLE + 44;
    svg.setAttribute("viewBox", `0 0 ${breite} ${h}`);
    const wagenH = 4 * CH + AISLE + 34;

    let out = `<rect x="4" y="${Y0 - 12}" width="${breite - 8}" height="${wagenH}" rx="14" fill="var(--wagen)" stroke="var(--border)"/>`;

    /* Zonenbänder (Ruhe-/Kinderbereich) */
    for (const [zone, fill, text] of [["RUHE", "var(--ruhe)", "Ruhebereich"], ["KINDER", "var(--kinder)", "Kinderbereich"]]) {
      const xsZone = plan.sitzplaetze.filter((s) => s.art === "SITZ" && s.zone === zone).map((s) => xs.get(s.reihe));
      if (!xsZone.length) continue;
      const x1 = Math.min(...xsZone), x2 = Math.max(...xsZone) + SW + 8;
      out += `<rect x="${x1}" y="${Y0 - 8}" width="${x2 - x1}" height="${wagenH - 8}" rx="8" fill="${fill}"/>`;
      out += `<text class="band-label" x="${(x1 + x2) / 2}" y="${Y0 + wagenH - 18}" text-anchor="middle">${text}</text>`;
    }

    rows.forEach((r) => { out += `<text x="${xs.get(r) + SW / 2 + 4}" y="${Y0 - 18}" text-anchor="middle" font-size="11" fill="var(--muted)">${r}</text>`; });
    out += ["A", "B", "C", "D"].map((l, c) => `<text x="22" y="${colY(c) + 27}" text-anchor="middle" font-size="12" fill="var(--muted)">${l}</text>`).join("");

    /* Tische: vier Plätze (zwei Reihen gegenüber, zwei Plätze nebeneinander) um einen Tisch */
    for (const r0 of rows) {
      if (r0 % 2 !== 0 || !rows.includes(r0 + 1)) continue;
      for (const [c0, c1] of [[0, 1], [2, 3]]) {
        const vier = plan.sitzplaetze.filter((s) => (s.reihe === r0 || s.reihe === r0 + 1) && (s.spalte === c0 || s.spalte === c1));
        if (vier.length !== 4 || !vier.every((s) => s.hatTisch)) continue;
        const tx = xs.get(r0) + 4 + SW + 4, tw = xs.get(r0 + 1) + 4 - tx - 4;
        out += `<rect x="${tx}" y="${colY(c0) + 8}" width="${tw}" height="${colY(c1) - colY(c0) + SH - 8}" rx="4" fill="var(--tisch)"><title>Tisch mit 4 Plätzen</title></rect>`;
      }
    }

    const bezeichnung = { FREI: "verfügbar", BELEGT: "belegt", VORGEMERKT: "von anderer Anfrage vorgemerkt", GRUPPENKONTINGENT: "für Gruppen reserviert", SONDERPLATZ: "Sonderplatz, nicht angefordert", ANDERE_ZONE: "andere Zone" };

    for (const s of plan.sitzplaetze) {
      const x = xs.get(s.reihe);
      const sonder = s.art !== "SITZ";
      const y = colY(s.spalte);
      const cls = ["seat", s.zustand === "FREI" ? "available" : s.zustand === "BELEGT" ? "taken" : s.zustand === "VORGEMERKT" ? "held" : s.zustand === "GRUPPENKONTINGENT" ? "group" : s.zustand === "SONDERPLATZ" ? "sonder" : "other"];
      if (sonder) cls.push("sonder");
      if (gewaehlt.has(s.id)) cls.push("gewaehlt");
      if (neu.has(s.id)) cls.push("neu");
      const name = s.art === "ROLLSTUHL" ? "Rollstuhlplatz" : s.art === "FAHRRAD" ? "Fahrradstellplatz" : "Sitz " + s.id;
      const label = `${name}, ${gewaehlt.has(s.id) ? "gewählt" : neu.has(s.id) ? "neu reserviert" : bezeichnung[s.zustand]}`;
      const zonenText = s.zone === "RUHE" ? ", Ruhebereich" : s.zone === "KINDER" ? ", Kinderbereich" : "";
      out += `<g class="${cls.join(" ")}" data-id="${s.id}" tabindex="${s.zustand === "FREI" ? 0 : -1}" role="button" aria-label="${label}">
        <title>${label}${s.art === "SITZ" ? zonenText : ""}</title>
        <rect class="body" x="${x + 4}" y="${y + 4}" width="${SW}" height="${SH}" rx="6"/>
        ${s.amFenster && !sonder ? `<rect x="${x + 4}" y="${s.spalte === 0 ? y + 1 : y + CH - 6}" width="${SW}" height="4" rx="2" fill="#4aa3ff"/>` : ""}
        ${sonder
          ? `<text class="symbol" x="${x + 4 + SW / 2}" y="${y + 4 + SH / 2 + 7}" text-anchor="middle">${s.art === "ROLLSTUHL" ? "♿" : "🚲"}</text>`
          : `<text x="${x + 4 + SW / 2}" y="${y + 25}" text-anchor="middle" font-weight="600">${s.id}</text>
             ${s.zone === "RUHE" ? `<text class="mini" x="${x + SW - 4}" y="${y + 17}" text-anchor="end">Zz</text>` : ""}
             ${s.zone === "KINDER" ? `<text class="mini" x="${x + SW - 4}" y="${y + 17}" text-anchor="end">☺</text>` : ""}
             ${s.hatSteckdose ? `<text class="mini" x="${x + 4 + SW / 2}" y="${y + 39}" text-anchor="middle">⚡</text>` : ""}`}
      </g>`;
    }
    svg.innerHTML = out;
  }

  return { zeichnen };
})();
