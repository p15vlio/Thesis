# Multi-stage build of the backend and the web panel from the _ProjectThesis monorepo.
# Build context: the _ProjectThesis folder (see docker-compose.yml). Needs BuildKit (default in Docker Desktop
# and Docker Engine 23+) for the cache mounts below.

# --- Stage 1: Build ---
# Gradle itself runs on the JDK 21 of this image. The Kotlin modules declare jvmToolchain(17), so Gradle
# provisions a JDK 17 toolchain through the foojay resolver on the first build; Node.js, Yarn and Binaryen
# (for the Wasm build) are also downloaded by Gradle. Everything Gradle downloads lives in /root/.gradle,
# which is a BuildKit cache mount: it survives between builds, so a source change does not download the
# dependencies, the toolchain or Node.js again.
FROM eclipse-temurin:21 AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*

COPY . .
# A Windows checkout without .gitattributes may have given gradlew CRLF line endings ("./gradlew: not found").
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Production Wasm build (optimised with Binaryen) instead of the much larger development build.
RUN --mount=type=cache,id=guestdesk-gradle,target=/root/.gradle \
    ./gradlew :backend:installDist :owner-web:wasmJsBrowserDistribution --no-daemon

# --- Stage 2: Backend (JRE + Ktor) ---
FROM eclipse-temurin:21-jre-alpine AS backend
WORKDIR /app
COPY --from=builder /app/backend/build/install/backend ./
EXPOSE 8080
ENTRYPOINT ["bin/backend"]

# --- Stage 3: Frontend (nginx + owner-web static files) ---
FROM nginx:alpine AS frontend
COPY --from=builder /app/owner-web/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
