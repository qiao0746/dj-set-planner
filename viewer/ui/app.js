(function () {
  const dropzone = document.getElementById("dropzone");
  const fileInput = document.getElementById("fileInput");
  const jsonPaste = document.getElementById("jsonPaste");
  const loadPaste = document.getElementById("loadPaste");
  const errorEl = document.getElementById("error");
  const content = document.getElementById("content");

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.remove("hidden");
  }

  function clearError() {
    errorEl.textContent = "";
    errorEl.classList.add("hidden");
  }

  function parseJson(text) {
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error("Invalid JSON: " + e.message);
    }
  }

  function esc(s) {
    if (s == null) return "";
    const d = document.createElement("div");
    d.textContent = String(s);
    return d.innerHTML;
  }

  function trackRow(t, i, extraCols) {
    const e10 =
      t.djEnergy != null && t.djEnergy > 0
        ? Number(t.djEnergy).toFixed(2)
        : t.energy != null
          ? (Number(t.energy) * 10).toFixed(2)
          : "—";
    const ten = t.tension != null ? Number(t.tension).toFixed(2) : "—";
    const inten = t.intensity != null ? Number(t.intensity).toFixed(2) : "—";
    let extra = "";
    if (extraCols) {
      extra = extraCols(t, i);
    }
    return `<tr>
      <td class="num">${i + 1}</td>
      <td>${esc(t.title)}</td>
      <td class="num">${t.bpm != null ? esc(t.bpm) : "—"}</td>
      <td class="num">${esc(t.key)}</td>
      <td class="num">${e10}</td>
      <td class="num">${ten}</td>
      <td class="num">${inten}</td>
      ${extra}
    </tr>`;
  }

  function renderSetPlan(data) {
    const tracks = data.orderedTracks || [];
    const trans = data.transitions || [];
    let html = `<section>
      <h2>Set plan</h2>
      <div class="meta">
        <span><strong>Style</strong> ${esc(data.style || "—")}</span>
        <span><strong>Curve</strong> ${esc(data.targetCurve || "—")}</span>
        <span><strong>Overall score</strong> ${data.overallScore != null ? esc(data.overallScore) : "—"}</span>
      </div>`;

    const eSeries = tracks.map((t) =>
      t.djEnergy != null && t.djEnergy > 0
        ? Number(t.djEnergy)
        : (Number(t.energy) || 0) * 10
    );
    const tSeries = tracks.map((t) => Number(t.tension) || 0);
    html += renderDualChart("Track energy & tension (0–10)", eSeries, tSeries, ["djEnergy×10?", "tension"]);

    html += `<table><thead><tr>
      <th>#</th><th>Title</th><th>BPM</th><th>Key</th><th>Energy</th><th>Tension</th><th>Intensity</th>
    </tr></thead><tbody>`;
    tracks.forEach((t, i) => {
      html += trackRow(t, i);
    });
    html += `</tbody></table>`;

    if (trans.length) {
      const idToTitle = {};
      tracks.forEach((t) => {
        if (t && t.id) idToTitle[t.id] = t.title && String(t.title).trim() !== "" ? t.title : t.id;
      });
      html += `<h3 style="margin-top:1.5rem;font-size:0.95rem;color:var(--muted)">Transitions</h3>
      <table><thead><tr><th>From → To</th><th>Score</th><th>Mix</th><th>Reasons</th></tr></thead><tbody>`;
      trans.forEach((tr) => {
        const reasons = Array.isArray(tr.reasons) ? tr.reasons.join("; ") : "";
        const fromL = idToTitle[tr.fromTrackId] || tr.fromTrackId;
        const toL = idToTitle[tr.toTrackId] || tr.toTrackId;
        html += `<tr>
          <td><small>${esc(fromL)} → ${esc(toL)}</small></td>
          <td class="num">${esc(tr.compatibilityScore)}</td>
          <td>${esc(tr.mixSuggestion)}</td>
          <td class="slot-reason">${esc(reasons)}</td>
        </tr>`;
      });
      html += `</tbody></table>`;
    }
    html += `</section>`;
    return html;
  }

  function linePath(values, width, height, pad, minV, maxV) {
    if (!values.length) return "";
    const n = values.length;
    const span = maxV - minV || 1;
    const pts = values.map((v, i) => {
      const x = pad + (i / Math.max(1, n - 1)) * (width - 2 * pad);
      const y = height - pad - ((v - minV) / span) * (height - 2 * pad);
      return `${x},${y}`;
    });
    return pts.join(" ");
  }

  function renderDualChart(title, seriesA, seriesB, labels) {
    const w = 800;
    const h = 120;
    const pad = 12;
    const all = [...seriesA, ...seriesB].filter((x) => !Number.isNaN(x));
    const minV = Math.min(0, ...all);
    const maxV = Math.max(10, ...all);
    const pathA = linePath(seriesA, w, h, pad, minV, maxV);
    const pathB = linePath(seriesB, w, h, pad, minV, maxV);
    return `<div class="chart-wrap">
      <h3>${esc(title)}</h3>
      <svg class="chart-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid meet">
        <rect width="${w}" height="${h}" fill="transparent" />
        <polyline fill="none" stroke="#34d399" stroke-width="2" points="${pathA}" />
        <polyline fill="none" stroke="#60a5fa" stroke-width="2" points="${pathB}" />
      </svg>
      <div style="font-size:0.75rem;color:var(--muted)">
        <span style="color:#34d399">● ${esc(labels[0])}</span>
        &nbsp;&nbsp;
        <span style="color:#60a5fa">● ${esc(labels[1])}</span>
      </div>
    </div>`;
  }

  function renderShapePlan(plan, modeKey) {
    const te = plan.targetEnergyCurve || [];
    const tt = plan.targetTensionCurve || [];
    const tracks = plan.orderedTracks || [];
    const debug = plan.slotDebug || [];

    let html = `<section data-mode-tab="${esc(modeKey)}">
      <h2>Mode: ${esc(modeKey)}</h2>`;
    html += renderDualChart("Target curves (0–10)", te.map(Number), tt.map(Number), ["target energy", "target tension"]);

    const eTr = tracks.map((t) =>
      t.djEnergy != null && t.djEnergy > 0
        ? Number(t.djEnergy)
        : (Number(t.energy) || 0) * 10
    );
    const tTr = tracks.map((t) => Number(t.tension) || 0);
    html += renderDualChart("Assigned tracks (energy & tension)", eTr, tTr, ["track energy", "tension"]);

    html += `<table><thead><tr>
      <th>#</th><th>Title</th><th>BPM</th><th>Key</th><th>Energy</th><th>Tension</th><th>Intensity</th>
      <th>Slot fit</th><th>ΔE/ΔT/ΔI</th><th>In / Out</th><th>Note</th>
    </tr></thead><tbody>`;

    tracks.forEach((t, i) => {
      const d = debug[i] || {};
      const dist = `${d.energyDistance != null ? d.energyDistance.toFixed(2) : "—"} / ${d.tensionDistance != null ? d.tensionDistance.toFixed(2) : "—"} / ${d.intensityDistance != null ? d.intensityDistance.toFixed(2) : "—"}`;
      const trans = `${d.transitionInScore != null ? d.transitionInScore.toFixed(2) : "—"} / ${d.transitionOutScore != null ? d.transitionOutScore.toFixed(2) : "—"}`;
      html += `<tr>
        <td class="num">${i + 1}</td>
        <td>${esc(t.title)}</td>
        <td class="num">${esc(t.bpm)}</td>
        <td class="num">${esc(t.key)}</td>
        <td class="num">${t.djEnergy != null ? esc(t.djEnergy) : "—"}</td>
        <td class="num">${esc(t.tension)}</td>
        <td class="num">${esc(t.intensity)}</td>
        <td class="num">${d.slotFitScore != null ? d.slotFitScore.toFixed(2) : "—"}</td>
        <td class="num" style="font-size:0.75rem">${esc(dist)}</td>
        <td class="num" style="font-size:0.75rem">${esc(trans)}</td>
        <td class="slot-reason">${esc(d.reason)}</td>
      </tr>`;
    });
    html += `</tbody></table></section>`;
    return html;
  }

  function renderShapeFromFirst(data) {
    const modes = data.shapeByMode || {};
    const keys = Object.keys(modes);
    if (!keys.length) return "<p>No shapeByMode found.</p>";

    let tabs = `<div class="tabs" id="modeTabs">`;
    keys.forEach((k, i) => {
      tabs += `<button type="button" class="tab${i === 0 ? " active" : ""}" data-tab="${esc(k)}">${esc(k)}</button>`;
    });
    tabs += `</div><div id="modePanels">`;

    keys.forEach((k, i) => {
      tabs += `<div class="mode-panel${i === 0 ? "" : " hidden"}" data-panel="${esc(k)}">`;
      tabs += renderShapePlan(modes[k], k);
      tabs += `</div>`;
    });
    tabs += `</div>`;

    let seed = "";
    if (data.seedTracks && data.seedTracks.length) {
      seed = `<section><h2>Seed list (${esc(data.seedTrackCount)} tracks)</h2><table><thead><tr>
        <th>#</th><th>Title</th><th>BPM</th><th>Key</th><th>Energy</th><th>Tension</th><th>Intensity</th>
      </tr></thead><tbody>`;
      data.seedTracks.forEach((t, i) => {
        seed += trackRow(t, i);
      });
      seed += `</tbody></table></section>`;
    }

    return seed + tabs;
  }

  function wireTabs() {
    const tabs = document.querySelectorAll("#modeTabs .tab");
    const panels = document.querySelectorAll("#modePanels .mode-panel");
    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        const name = tab.getAttribute("data-tab");
        tabs.forEach((t) => t.classList.remove("active"));
        tab.classList.add("active");
        panels.forEach((p) => {
          p.classList.toggle("hidden", p.getAttribute("data-panel") !== name);
        });
      });
    });
  }

  function render(data) {
    clearError();
    let html = "";

    if (data.shapeByMode) {
      html += renderShapeFromFirst(data);
    } else if (data.orderedTracks && Array.isArray(data.orderedTracks) && !data.seedTracks) {
      html += renderSetPlan(data);
    } else if (Array.isArray(data)) {
      html += `<section><h2>Tracks (${data.length})</h2><table><thead><tr>
        <th>#</th><th>Title</th><th>BPM</th><th>Key</th><th>Energy</th><th>Tension</th><th>Intensity</th>
      </tr></thead><tbody>`;
      data.forEach((t, i) => {
        html += trackRow(t, i);
      });
      html += `</tbody></table></section>`;
    } else {
      html = `<p class="error">Unknown JSON shape. Use SetPlan output, shape-from-first output, or a track array.</p>`;
    }

    content.innerHTML = html;
    if (data.shapeByMode) wireTabs();
  }

  function loadText(text) {
    const data = parseJson(text);
    render(data);
  }

  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("dragover", (e) => {
    e.preventDefault();
    dropzone.classList.add("dragover");
  });
  dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
  dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    const f = e.dataTransfer.files[0];
    if (f) {
      f.text().then(loadText).catch((err) => showError(err.message));
    }
  });

  fileInput.addEventListener("change", () => {
    const f = fileInput.files[0];
    if (f) {
      f.text().then(loadText).catch((err) => showError(err.message));
    }
  });

  loadPaste.addEventListener("click", () => {
    try {
      loadText(jsonPaste.value);
    } catch (e) {
      showError(e.message);
    }
  });
})();
