#!/usr/bin/env bash
# Belt-and-braces cleanup after a regression run. Removes everything the run
# could have created, matched by the CI naming prefixes - regardless of how far
# the run got before dying. Safe to run any time: production instances never
# use these prefixes (client "ci" is reserved for this pipeline).
#
# Never uses `set -e`: cleanup must keep going past individual failures.
set -u

root="${CI_HOST_VOLUME_ROOT:-/docker/volumefs/monohull-ci}"

echo "[janitor] Containers matching monohull-ci-*"
docker ps -aq --filter 'name=^monohull-ci-' | xargs -r docker rm -f

echo "[janitor] Networks matching monohull-ci-*"
docker network ls --format '{{.Name}}' | grep '^monohull-ci-' | xargs -r -n1 docker network rm 2>/dev/null

echo "[janitor] Volumes matching monohull-ci-* / made-monohull-ci-*"
docker volume ls -q | grep -E '^(monohull-ci-|made-monohull-ci-)' | xargs -r docker volume rm -f 2>/dev/null

if [ -d "${root}" ]; then
  echo "[janitor] Host env dirs under ${root}"
  # Env subdirs are created by containers and end up root-owned; sweep from a
  # container (same trick Monohull's own teardown uses) so no sudo is needed.
  docker run --rm -v "${root}:/sweep" busybox sh -c 'rm -rf /sweep/* /sweep/.[!.]*' 2>/dev/null
fi

echo "[janitor] Done"
exit 0
