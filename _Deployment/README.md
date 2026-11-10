# _Deployment

Everything needed to run Guest Desk on one computer with Docker. The full guide is the
[Installation](https://p15vlio.github.io/Thesis/gettingStarted/installation) page of the project site.

| Run | What it does |
|---|---|
| `start-all.bat` (Windows) / `bash start-all.sh` (Linux, macOS) | First start: checks the dependencies, creates `docker/.env`, builds and starts the stack, seeds the demo data |
| `manage.bat` / `bash manage.sh` `up`, `down`, `status`, `logs`, `reset` | Day-to-day control of the running stack (`reset` deletes the database) |

| Folder | Contents |
|---|---|
| `apk/` | The kiosk app for Android tablets: `guestdesk-release.apk` (signed) and `guestdesk-debug.apk` |
| `docker/` | `docker-compose.yml`, `app.Dockerfile`, `.env.example`; the generated `.env` with the secrets lives here |
| `steps/` | The three steps of `start-all` (`01-check-dependencies`, `02-build-and-start`, `03-seed`), each as `.bat`/`.ps1` and `.sh`, and their shared helpers (`common.ps1`, `common.sh`) |
| `python/` | `seed_admin.py` (first platform administrator), `seed_data.py` (demo data), `verify_flow.py` (end-to-end check of the API), `requirements.txt`; the scripts create `python/.venv` |
