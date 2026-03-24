# dj-set-planner

A DJ intelligence tool that transforms track metadata and audio features into optimized set orders with energy curves, transition suggestions, and mode-based (club / rave / house party) vibe shaping.

## Overview

Minimal Java 21 CLI (Maven) + Python audio analyzer.

## Project layout

```
src/main/java/com/djset/   — application code
src/test/java/com/djset/   — JUnit tests
python/                    — analyzer script + requirements (see python/README.md)
viewer/                    — interactive web planner (viewer/interactive/), static viewer, viewer/ui/
data/samples/              — sample track JSON for examples
pom.xml
```

## Commands

- `plan` (stub)
- `analyze`
- `transitions` (stub)
- `recommend`
- `build-set`
- `build-from-first`
- `shape-from-first`
- `ui` — local web server: **interactive planner** + static viewer assets

## Interactive web planner (analyze + plan)

From the project root:

**Windows (PowerShell)**
```powershell
.\run.ps1 ui
```

**Windows (cmd.exe)**
```bat
run.cmd ui
```

**macOS / Linux**
```bash
mvn -q -DskipTests compile exec:java "-Dexec.args=ui"
```

Then open **http://127.0.0.1:8787/** (serves `interactive/index.html`). You can:

- Enter your **music folder** path (MP3s)
- Choose **analyze mode** (`standard` / `rekordbox-like`)
- **Plan mode** (default **Default**): same as **`build-from-first`** — opener stays **#1**, following tracks chosen for **transition scoring**; response includes **`transitions`**. Optional **target curve** (e.g. `gradual-rise`).
- **Plan mode Shaped**: **set-shape** modes (all / club / rave / house_party); optional **opening track** pins **#1** for the curve pass.
- Set **track count** (1–50); **opening track** optional (default plan: empty = first file in analyze order)
- Click **Analyze & generate** — **`POST /api/run`** (`planMode`: `default` default, or `shaped`; `targetCurve` for default plan; legacy `greedy` is accepted)
- **Analyze cache (UI):** results are written under **`%USERPROFILE%\.djset\analyze-cache\`** — one subfolder per **music folder** (hash of its absolute path), then a **`{options}.json`** file per combo of analyze mode, default genre, Python command, and analyzer script. A sidecar **`{options}.json.cache.json`** stores per-track Python features (same as CLI). Optional JSON field **`reanalyze`: `true`** (Advanced: *Force re-analyze*) clears that run’s per-file cache.

Static files and the offline drag-drop viewer live under **`viewer/`** as well (`/ui/`, `djset-result-viewer.html`).

## Result viewer (offline JSON)

**Recommended:** open **`viewer/djset-result-viewer.html`** in your browser (double-click or open with Chrome/Edge/Firefox).  
Single file — **no server**. **Drop** a JSON file, **choose** one, or **paste** CLI output.

Supported JSON:

- `build-from-first` / `plan` style **`SetPlan`** (`orderedTracks`, `transitions`, …)
- **`shape-from-first`** / **interactive API** output (`shapeByMode`, `seedTracks`, …)
- A plain **array of tracks** (e.g. analyzer output)

Copy **`viewer/djset-result-viewer.html`** anywhere; it stays self-contained.

**Optional** — serve `viewer/` with Java (includes interactive + `/ui/`):

```powershell
.\run.ps1 ui
```

Then **http://127.0.0.1:8787/** and **http://127.0.0.1:8787/interactive/** (or `mvn -q exec:java -Dexec.mainClass=com.djset.ui.UiServer`).

## Run

**Windows (PowerShell)**
```powershell
.\run.ps1 --help
.\run.ps1 plan --help
.\run.ps1 analyze --help
.\run.ps1 transitions --help
```

**Windows (cmd.exe)**
```bat
run.cmd --help
run.cmd plan --help
run.cmd analyze --help
run.cmd transitions --help
```

**macOS / Linux**
```bash
mvn -q -DskipTests compile exec:java "-Dexec.args=--help"
mvn -q -DskipTests compile exec:java "-Dexec.args=plan --help"
mvn -q -DskipTests compile exec:java "-Dexec.args=analyze --help"
mvn -q -DskipTests compile exec:java "-Dexec.args=transitions --help"
```

## Plan command example

```powershell
.\run.ps1 plan --input .\data\samples\sample-tracks.json --style house --target-curve gradual-rise --count 5
```

`--count` is optional and accepts `1-50`.
If not enough relevant tracks are available, the planner may return fewer tracks.

## Recommend next songs from current song

```powershell
.\run.ps1 recommend --input .\sample-tracks.json --current-track-id t1 --count 5
```

## Build set from selected songs

```powershell
.\run.ps1 build-set --input .\data\samples\sample-tracks.json --selected-ids t1,t2,t3 --style house --target-curve gradual-rise --count 3
```

## Build DJ list from first song

```powershell
.\run.ps1 build-from-first --input .\data\samples\e2e-tracks-rb.json --first-track-id 5d0d2ed0-c9a9-3125-adce-6349c2b6435e --count 8 --style tech-house --target-curve gradual-rise
```

You can also start by title (partial, case-insensitive):

```powershell
.\run.ps1 build-from-first --input .\data\samples\e2e-tracks-rb.json --first-track-title "coming up" --count 8 --style tech-house --target-curve gradual-rise
```

## Compare set-shape modes from first track

```powershell
.\run.ps1 shape-from-first --input .\data\samples\e2e-tracks-rb.json --first-track-title "coming up" --count 8
```

## Analyze MP3 metadata to JSON

```powershell
.\run.ps1 analyze --music-dir C:\music\set1 --output .\tracks-from-mp3.json --default-genre house --python-cmd python --analyzer-script .\python\audio_analyzer.py --analyze-workers 4
```

Setup check only:

```powershell
.\run.ps1 analyze --check-setup --python-cmd python --analyzer-script .\python\audio_analyzer.py
```

Current analyzer output includes metadata plus offline audio feature estimation:
- reads MP3 tags (`title`, `artist`, `genre`)
- reads track duration
- estimates `bpm`, `key`, `energy`, `danceability` via Python analyzer
- writes `Track` JSON entries for use by `plan`

Quality refinements currently included:
- BPM half/double normalization into a DJ-friendly range
- key confidence gating (low-confidence key is emitted as `null`)
- improved energy/danceability feature blending

If Python or dependencies are unavailable, analyzer gracefully falls back to metadata-only values.
`--analyze-workers` controls parallel file analysis (range `1-32`, default `1`).
`--music-dir` is an alias of `--input-dir`; use either to point at your local MP3 folder.
`--mode` supports `standard` and `rekordbox-like` (segment-smoothed key with softer confidence gate).
`--reanalyze` ignores cache and recomputes all files.

Sidecar cache `{output}.cache.json` keys include the analyzer **mode**, so switching `--mode` with the same `--output` no longer reuses features from the wrong mode. Entries from older builds (keys without a `|standard` / `|rekordbox-like` suffix) are ignored on load so they are not mixed with new keys.

Install Python deps:

```powershell
pip install -r .\python\requirements.txt
```

## Java packages

- `com.djset.cli` — Picocli commands
- `com.djset.model`, `com.djset.service`, `com.djset.scorer`, `com.djset.util`
- `com.djset.ui` — optional `HttpServer` for `viewer/ui/`
