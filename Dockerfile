# ---- Stage 1: Build ----
# --platform=$BUILDPLATFORM: the jar is architecture-independent, so multi-arch buildx
# runs Maven/npm once natively instead of re-compiling everything under QEMU per arch.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
COPY java/ java/
COPY frontend/ frontend/

# frontend-maven-plugin downloads Node v20.11.0 + npm 10.2.4 and builds the React app internally.
# No Maven cache mount is used so that dependency version changes (e.g. Tomcat upgrades)
# always take effect — docker builder prune clears the layer cache but not cache mount volumes.
RUN mvn clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre

# git checks out PR branches into the shared workspace volume for PR builds;
# openssh-client lets it clone over SSH (deploy-key repos).
RUN apt-get update \
    && apt-get install -y --no-install-recommends git openssh-client ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/target/monohull.jar app.jar

# Ship the mock-receiver build context so Monohull can build monohull/mock-receiver
# on demand the first time an env opts in to the addon. The image is built via
# docker-java against the host daemon over the mounted socket, but the context
# is read from this directory inside the Monohull container.
COPY docker/ ./docker/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
