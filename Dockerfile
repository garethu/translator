# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test && npm run build

FROM maven:3.9-eclipse-temurin-25 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src/ ./src/
COPY --from=frontend-build /workspace/frontend/dist/ /workspace/frontend/dist/
RUN mvn -B -ntp -Dexec.skip=true verify

FROM eclipse-temurin:25-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --user-group --create-home translator
WORKDIR /app
COPY --from=backend-build --chown=translator:translator /workspace/backend/target/translator-*.jar ./translator.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080
USER translator
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/translator/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/translator.jar"]
