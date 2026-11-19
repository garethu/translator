# Translator Backend

Spring Boot 4.1.1 / Spring AI 2.0.1, Java 25. Uses the native Ollama API,
one shared stateless ChatClient, and one structured model call returning both
formal and informal translations in the selected target language. Both fields
must pass validation before the pair is returned. No partial pair is returned.
No chat memory, history, database, or model installation on startup.

## Run and Test

Use Maven and a JDK 25 installation. The build must not run with the machine's
default JDK 21. A checksum-verified Temurin JDK was extracted for verification at
the following user-local location:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\TranslatorTools\jdk-25.0.4.1+1"
mvn -f backend/pom.xml verify
mvn -f backend/pom.xml spring-boot:run
```

Run these commands from the repository root; substitute your own JDK 25 path
where needed. Setting JAVA_HOME in this terminal does not change the system default.
The packaged app listens at `http://localhost:8080/translator/`. Maven builds the
Vue frontend and bundles it into the executable JAR, so Vite is not required at
runtime. Node/npm are build prerequisites only. Ollama must be running and the
configured model must already be available for real translations. Tests use a
mock ChatModel and a local fake HTTP server, never a real Ollama model.

## API

`GET /translator/api/languages` returns a JSON array of 44 languages, including EU24, the
additional European languages requested, and Arabic. Each item has an ISO 639-1
`code`, English `label`, and boolean `rtl` (true for Arabic).

```json
{"code":"ar","label":"Arabic","rtl":true}
```

Both source and target must be explicit catalog language codes. Automatic source
detection (`auto`) is not supported.

`POST /translator/api/translations`, with `Content-Type: application/json`:

```json
{"text":"Hello, could you send the report?","sourceLanguage":"en","targetLanguage":"nl"}
```

Example response shape (wording depends on the model):

```json
{"formal":"Kunt u het rapport sturen?","informal":"Kun je het rapport sturen?"}
```

Text must be nonblank and at most 6000 Java characters (UTF-16 code units).
Language codes are case-sensitive and must match the catalog. Source and target
must differ. Source text is passed verbatim as an
untrusted user message, not interpolated into a prompt template. The resource
system prompt requests preservation of meaning, facts, names, and formatting.
Native Ollama structured output supplies a formal/informal record schema;
`entity()` converts the paired response. Empty, missing, malformed, non-string
or unexpected fields fail rather than returning a partial pair. The Arabic-script
safeguard checks both fields independently. It is not comprehensive language or
accuracy verification.

Errors use `application/problem+json` with fixed details, without source text,
model output, stack traces, or upstream exception messages:

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON, text, language code, or identical source/target |
| 429 | All translation permits are in use; `Retry-After: 1` |
| 502 | Invalid or incomplete model translation output |
| 503 | Ollama unavailable, upstream failure, or HTTP timeout |

## Environment

| Variable | Default |
| --- | --- |
| `SERVER_ADDRESS` | `127.0.0.1` |
| `SERVER_PORT` | `8080` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `OLLAMA_MODEL` | `gemma3:4b` |
| `OLLAMA_CONNECT_TIMEOUT` | `5s` |
| `OLLAMA_READ_TIMEOUT` | `120s` |
| `TRANSLATOR_MAX_CONCURRENT` | `1` |

The synchronous Ollama RestClient uses a JDK HTTP request factory with explicit
connect and read timeouts. A zero-retry RetryTemplate prevents repeated model
requests from multiplying the timeout. One model call produces both results.
The semaphore covers that call and validation and uses immediate tryAcquire:
busy requests are rejected, not queued. A timed-out HTTP call does not guarantee
Ollama immediately cancels its server-side generation.

AI and HTTP-client payload logging are disabled; do not enable request/body debug
logging for sensitive text. Only the actuator health endpoint is exposed, without
health details. There is no authentication or permissive CORS configuration. Keep
the default loopback binding; remote exposure requires a separate security design.
Pointing OLLAMA_BASE_URL at a remote server sends source text to that server.

The catalog declares accepted language codes, not guaranteed model quality.
Translation accuracy, register, formatting, and prompt-injection resistance need
real-model evaluation. Formal and informal variants may legitimately be identical.