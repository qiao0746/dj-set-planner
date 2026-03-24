# Python — audio analyzer

Offline feature extraction for MP3s (invoked by the Java `analyze` command).

- **`audio_analyzer.py`** — script entrypoint (stdin/stdout JSON; called per file by `AnalyzeService`)
- **`requirements.txt`** — `pip install -r requirements.txt`

Default path from the project root: `python/audio_analyzer.py` (override with `--analyzer-script`).
