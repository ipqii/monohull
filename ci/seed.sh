#!/usr/bin/env bash
# Seed the throwaway CI Monohull instance and launch the vanilla profile.
#
# Required env:
#   CI_MONOHULL_URL       e.g. http://localhost:8899
#   CI_API_KEY            per-run bearer key (matches the compose instance)
#   CI_RUN_PROJECT        per-run unique project, e.g. r<github-run-id>
#   CI_REGISTRY_URL / CI_REGISTRY_USERNAME / CI_REGISTRY_PASSWORD
#   CI_VANILLA_APP_IMAGE / CI_VANILLA_DB_IMAGE / CI_VANILLA_ADM_IMAGE
#   CI_HOST_VOLUME_ROOT   CI-only root, e.g. /docker/volumefs/monohull-ci
#   CI_AWS_DIR            host dir with AWS creds for the DB restore
#
# Output: prints the launched environment id on stdout (last line), and appends
# env_id=<id> to $GITHUB_OUTPUT when running under GitHub Actions.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
auth=(-H "Authorization: Bearer ${CI_API_KEY}")

echo "[seed] Setting registry credential for ${CI_REGISTRY_URL}" >&2
curl -fsS -X PUT "${CI_MONOHULL_URL}/api/config/registry" "${auth[@]}" \
  -H 'Content-Type: application/json' \
  -d "$(printf '{"url":"%s","username":"%s","password":"%s","description":"ci"}' \
        "${CI_REGISTRY_URL}" "${CI_REGISTRY_USERNAME}" "${CI_REGISTRY_PASSWORD}")" > /dev/null

echo "[seed] Rendering vanilla bundle (project ${CI_RUN_PROJECT})" >&2
bundle="$(mktemp)"
# shellcheck disable=SC2016
envsubst '${CI_RUN_PROJECT} ${CI_VANILLA_APP_IMAGE} ${CI_VANILLA_DB_IMAGE} ${CI_VANILLA_ADM_IMAGE} ${CI_HOST_VOLUME_ROOT} ${CI_AWS_DIR}' \
  < "${here}/vanilla.bundle.template.yaml" > "${bundle}"

echo "[seed] Importing bundle + launching environment" >&2
response="$(curl -fsS -X POST "${CI_MONOHULL_URL}/api/profiles/launch" "${auth[@]}" \
  -H 'Content-Type: application/x-yaml' --data-binary @"${bundle}")"
rm -f "${bundle}"

env_id="$(printf '%s' "${response}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["environment"]["id"])')"
env_name="$(printf '%s' "${response}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["environment"]["name"])')"
echo "[seed] Launched environment ${env_name} (id ${env_id})" >&2

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "env_id=${env_id}" >> "${GITHUB_OUTPUT}"
fi
echo "${env_id}"
