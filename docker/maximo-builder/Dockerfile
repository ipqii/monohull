# Ephemeral package-builder image. Monohull builds this on demand (like the mock-receiver)
# and spawns a throwaway container per build-package pipeline step: the env's workspace
# is bind-mounted, ant produces the addon zip, and Monohull copies /out/package.zip into the
# ADM container for deployment. JDK 8 so the 1.8-source/target Maximo compile is exact
# (the ADM container only carries javac 17). ant-contrib is taskdef'd from the project
# repo's own lib/, so no extra ant libraries are baked in.
FROM eclipse-temurin:8-jdk-jammy

# safe.directory "*": the workspace bind is often owned by another uid (the operator's
# host user) while the builder runs as root; without this, project build scripts that
# shell out to git (branch/commit stamping) die with "detected dubious ownership" and
# corrupt the artifact name. The builder is disposable and only sees mounted workspaces,
# so trusting all paths is safe here.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ant unzip git \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /out /workspace \
    && git config --system --add safe.directory "*"

WORKDIR /workspace
