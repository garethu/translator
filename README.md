# Translator

Local translation with Spring Boot 4.1.1, Spring AI 2.0.1, Java 25 and Vue 3.
Every request produces both a formal version for an external client and an
informal version for a colleague. No tone selection or chat memory is involved.
One model call translates the original into both formal and informal versions
in the selected target language. Both fields must pass validation before either
is returned. Invalid or incomplete output returns an error, never a partial pair.
The Arabic-script safeguard remains active for each result independently.

## Docker

Install Docker Engine with Docker Compose v2, or Docker Desktop on Windows
with Linux containers enabled. Run the following from the `Translator` directory.
No local Java, Maven or Node installation is needed for a Docker build.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps -a
docker compose logs -f translator ollama-init
```

Open **http://localhost:8080/translator/** after startup. The multi-stage build
runs frontend tests and the production build, then backend tests and JAR packaging.
The final image contains only Java 25, the bundled application and curl for its
health check; the application runs as a non-root user.

Compose runs Ollama separately and downloads `gemma3:4b` before starting the
application. The first download needs internet access, several GB of disk space
and may take a while. If it fails, inspect `docker compose logs ollama-init`,
resolve the download error and rerun `docker compose up -d`.
Models persist in the `ollama-data` volume across container replacement.
Ollama's port is not published to the host; only the application is exposed,
on host loopback. The app health check reports backend health, not model
readiness; the UI also checks Ollama availability, and model errors surface
when translating.

Edit `.env` to change `TRANSLATOR_PORT`, `OLLAMA_MODEL`, or `TRANSLATOR_IMAGE`.
After changing the model, run `docker compose up -d --force-recreate ollama-init translator`
to pull the selected model and recreate the app with matching configuration.
Keep the `/translator` context path.

```powershell
docker compose down
```

This stops the stack without deleting models. **`docker compose down --volumes`
also deletes the downloaded models.** Image rebuilds do not bundle or redownload
model weights when the requested model is already up to date.

### GPU acceleration

The default Compose setup uses CPU inference for portability. Allocate enough
Docker memory for both Ollama and the application; start with at least 8 GB
available to Docker for `gemma3:4b`. CPU inference may exceed the application's
120-second model timeout, especially on first load. See Configuration below for
timeout limits rather than increasing only the backend timeout.

For a supported NVIDIA GPU, configure Docker GPU support (NVIDIA Container
Toolkit on Linux, or a supported Docker Desktop/WSL2 configuration on Windows)
and add this override:

```yaml
# compose.gpu.yaml
services:
  ollama:
    gpus: all
```

Start with `docker compose -f compose.yaml -f compose.gpu.yaml up --build -d`.
The `gpus` setting requires Compose 2.30+ and a compatible driver/runtime.
Other GPU vendors require their own Ollama image/device configuration.
On macOS, host-installed Ollama is preferable for Apple GPU acceleration.
Verify GPU use with `docker compose exec ollama ollama ps` during inference.

### Use host-installed Ollama instead

Build and run only the application, without the Compose Ollama services:

```powershell
docker build -t translator:local .
docker run -d --name translator --publish 127.0.0.1:8080:8080 --env OLLAMA_BASE_URL=http://host.docker.internal:11434 --env OLLAMA_MODEL=gemma3:4b translator:local
```

Docker Desktop provides `host.docker.internal`. On Linux Docker Engine, add
`--add-host host.docker.internal:host-gateway` to `docker run`.
Install the selected model on the host first (`ollama pull gemma3:4b`).
Host Ollama must listen on an interface reachable from the container, not just
host loopback; configure its `OLLAMA_HOST` and restart Ollama if necessary.
Restrict access with the host firewall: Ollama has no built-in authentication,
so do not expose port 11434 to untrusted networks.

### Publish to Docker Hub

Create a Docker Hub repository (for example `your-dockerhub-user/translator`).
Choose its visibility deliberately; a public image distributes your compiled
application and bundled frontend. Do not include credentials in build arguments,
source files or the image. `.dockerignore` excludes local dependencies, build
outputs, environment files and unrelated workspace content.

Replace the example username below with your Docker Hub namespace:

```powershell
docker login
docker build --pull -t your-dockerhub-user/translator:0.1.0 -t your-dockerhub-user/translator:latest .
docker push your-dockerhub-user/translator:0.1.0
docker push your-dockerhub-user/translator:latest
```

These commands publish for the builder's platform. To publish for both common
Linux architectures, use a Buildx builder instead (cross-platform builds can be
much slower under emulation):

```powershell
docker buildx create --name translator-builder --driver docker-container --use
docker buildx build --platform linux/amd64,linux/arm64 --pull -t your-dockerhub-user/translator:0.1.0 -t your-dockerhub-user/translator:latest --push .
```

Create the named builder only once; reuse it with
`docker buildx use translator-builder`. Prefer versioned application tags for
deployments. The base images and Ollama currently use moving tags; pin tested
image digests for reproducible releases and update them deliberately.

On another machine, copy `compose.yaml` and `.env.example`, create `.env`, set
`TRANSLATOR_IMAGE=your-dockerhub-user/translator:0.1.0`, then run:

```powershell
docker compose pull
docker compose up -d --no-build
```

The published application image already includes the frontend. Ollama and model
weights are separate and are not pushed to your application repository.
Docker Hub distribution does not make the app safe to expose publicly; keep
the loopback port binding until authentication, TLS and request limits are added.

## Build the Standalone JAR

Building requires JDK 25, Maven 3.9+ and Node.js 22.12+ with npm (Node 24 LTS
recommended). Maven runs `npm ci` and the Vue production build automatically,
then bundles the assets, fonts and flags into the executable Spring Boot JAR.

```powershell
$env:JAVA_HOME = 'D:\path\to\jdk-25'
mvn -f backend/pom.xml clean verify
```

Deliver `backend/target/translator-0.0.1-SNAPSHOT.jar`. The recipient needs only
Java 25 and Ollama with the configured model installed; Node, npm, Maven and
Vite are not needed to run the delivered JAR.

```powershell
java -jar translator-0.0.1-SNAPSHOT.jar
```

Open **http://localhost:8080/translator/**. Embedded Tomcat serves both Vue and
the API at `/translator/api/...`. Ollama must be running separately.
The app checks Ollama through `/translator/api/availability` on startup and
every 10 seconds. Until a check succeeds, all buttons, language selectors and
text input are disabled. If Ollama or the backend cannot be reached, the app
shows "The translation service is not available." Controls unlock automatically
when the service returns. Checks use Ollama's lightweight version endpoint and
do not load the model or generate a translation; model errors are still handled
when translating.
If port 8080 is occupied, use `java -jar translator-0.0.1-SNAPSHOT.jar --server.port=8082`
and open `http://localhost:8082/translator/`. Keep the `/translator` context path;
the bundled frontend is built for that path. Stop a running copy before rebuilding
its JAR on Windows to avoid file-lock errors.
Also stop the Vite development server before a Maven build on Windows: `npm ci`
reinstalls dependencies and cannot replace native modules held open by Vite.

## Develop on Windows

Prerequisites: JDK 25, Maven 3.9+, Node.js 22.12+ (Node 24 LTS recommended),
and Ollama with `gemma3:4b` installed. If needed: `ollama pull gemma3:4b`.
Only one request is processed at a time to reduce memory pressure on a 16 GB machine.

From this repository, start the backend:

```powershell
./scripts/start-backend.ps1 -JavaHome 'D:\path\to\jdk-25'
```

If JAVA_HOME already points to JDK 25, omit the argument. The launcher also detects
a JDK 25 under `%LOCALAPPDATA%\TranslatorTools`, where the development JDK was
installed during setup. It does not change system Java settings. Backend default:
`http://localhost:8080/translator/`. This command builds both frontend and backend
before starting the standalone app; the second Vite terminal below is optional.

In another terminal:

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

For frontend hot reload, open `http://127.0.0.1:5173/translator/`. Both commands
are also available as VS Code tasks, `Translator: Backend` and `Translator: Frontend`.
Vite forwards `/translator/api` to the backend; it never calls Ollama from the browser.

## Language Preferences

Both languages start unselected. Choose a source and target to enable translation
with nonblank text; automatic source detection is not supported. Your choices
are saved in this browser automatically. A saved explicit pair remains selected until
changed or browser storage is cleared. Old automatic-source preferences require
a new source selection. The swap button works when both languages are selected.
Preferences are browser/origin-specific, not synced across devices. Submitted
text and results are not persisted. Both results have separate copy buttons.
Arabic output is right-to-left, and input direction follows the entered text.

### Initial Model Checks

The backend rejects predominantly Arabic-script output when a non-Arabic target
is selected, and instructions for Arabic register are only sent for Arabic
requests. This is a targeted script safeguard, not comprehensive language
identification: it cannot distinguish Dutch from other Latin-script languages
or guarantee correct meaning. A rejected pair is returned as an error, never as
one successful and one failed translation.

Live `gemma3:4b` smoke tests returned both fields for Dutch-to-English,
English-to-Dutch and English-to-Arabic. The first Dutch-to-English request took
about 24 seconds on this machine. These checks establish integration, not quality:
the reverse Dutch sample used an incorrect article, and the Arabic sample showed
meaning/register errors despite the prompt. Do not use the current model's output
unreviewed for client communications. Compare a stronger multilingual model on
representative messages before considering the translator production-ready.

The current catalog contains 44 languages: all 24 EU official languages plus
Albanian, Arabic, Armenian, Azerbaijani, Basque, Belarusian, Bosnian, Catalan,
Faroese, Georgian, Icelandic, Luxembourgish, Macedonian, Norwegian, Russian,
Scottish Gaelic, Serbian, Turkish, Ukrainian and Welsh. This is broad coverage,
not an exhaustive list of every European regional or minority language.
Catalog inclusion is not a guarantee of model accuracy. Review important business
translations, especially low-resource languages and Arabic dialects. Arabic defaults
to Modern Standard Arabic; informal means a relaxed register, not an assumed dialect.

## Configuration

Before starting the backend, environment variables can override `OLLAMA_MODEL`
(default `gemma3:4b`), `OLLAMA_BASE_URL` (default `http://localhost:11434`),
`SERVER_PORT` (default `8080`), and `OLLAMA_READ_TIMEOUT` (default `120s`).
If the backend port changes, set `BACKEND_URL` before starting Vite.
Source text is limited to 6,000 UTF-16 code units. First model loading can be slow.
The model read timeout is 120 seconds by default. The browser allows 135 seconds
for the whole request and the development proxy allows 150 seconds. Larger custom
model timeouts require increasing these limits and any production reverse-proxy
timeout too. Errors are shown rather than fake or partial results.

Outside Docker, the app binds to loopback by default (`SERVER_ADDRESS` overrides
this). The image binds to all container interfaces so Docker port forwarding can
reach it, while Compose publishes only on host loopback. The app has no
authentication. Do not expose it to a network
without adding security, request-body limits, TLS and a deployment review. Model
requests stay local only when Ollama is local. Fonts are bundled locally. No
analytics or external translation APIs are used. Source text is sent to Ollama
for processing but is not intentionally logged or stored by this application.

## Verification

```powershell
$env:JAVA_HOME = 'D:\path\to\jdk-25'
mvn -f backend/pom.xml verify
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend audit
```

Backend tests mock the model and exercise validation, paired structured output,
timeouts and errors; frontend tests cover preferences, paired rendering, copying,
Arabic direction and error states. Real model accuracy is a separate quality check.
For deployment, run the packaged executable JAR. Its embedded Tomcat serves the
frontend and API on the same origin. Vite is only a development server.