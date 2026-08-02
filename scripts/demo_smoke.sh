#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
API_BASE="${API_BASE:-$BASE_URL/api/v1}"
AI_BASE="${AI_BASE:-http://127.0.0.1:8000}"

require_bin() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_bin curl
require_bin jq

json_get() {
  jq -r "$1"
}

curl_json() {
  curl --retry 8 --retry-delay 1 --retry-connrefused --max-time 10 -fsS "$@"
}

health() {
  local name="$1"
  local url="$2"
  echo
  echo "== $name =="
  curl_json "$url" | jq
}

login() {
  local email="$1"
  local password="$2"
  curl_json -X POST "$API_BASE/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}"
}

create_work_item() {
  local token="$1"
  local payload="$2"
  curl_json -X POST "$API_BASE/work-items" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

post_no_body() {
  local token="$1"
  local url="$2"
  curl_json -X POST "$url" -H "Authorization: Bearer $token"
}

demo_flow() {
  local label="$1"
  local email="$2"
  local payload="$3"

  echo
  echo "== $label =="

  local auth
  auth="$(login "$email" "${DEMO_PASSWORD:-SmartWork!Demo2026}")"
  local token
  token="$(printf '%s' "$auth" | json_get '.accessToken')"
  local role_summary
  role_summary="$(printf '%s' "$auth" | json_get '.user.roles | join(", ")')"
  echo "login: $email ($role_summary)"

  local created
  created="$(create_work_item "$token" "$payload")"
  local id
  id="$(printf '%s' "$created" | json_get '.id')"
  local ref
  ref="$(printf '%s' "$created" | json_get '.referenceKey')"
  echo "created: $ref ($id)"

  local analysis
  analysis="$(post_no_body "$token" "$API_BASE/work-items/$id/analyze")"
  echo "analysis:"
  printf '%s' "$analysis" | jq '{status, provider, model, isMock, confidence, requiresMoreInformation, clarificationQuestions}'

  local route
  route="$(post_no_body "$token" "$API_BASE/work-items/$id/route")"
  echo "route:"
  printf '%s' "$route" | jq '{
    route: .decision.route,
    reasons: [.decision.reasons[].code],
    assignedWithoutHuman,
    assigneeName,
    approvalRequestId,
    candidatePoolTier
  }'
}

health "Backend health" "$BASE_URL/actuator/health"
health "AI readiness" "$AI_BASE/ready"

echo
echo "== Jira readiness =="
JIRA_STATUS_CODE="$(curl --retry 8 --retry-delay 1 --retry-connrefused --max-time 10 -s -o /tmp/iris-jira-status.json -w '%{http_code}' \
  -H "Authorization: Bearer $(login "admin@acme.io" "${DEMO_PASSWORD:-SmartWork!Demo2026}" | json_get '.accessToken')" \
  "$API_BASE/jira/status" || true)"
echo "http: $JIRA_STATUS_CODE"
cat /tmp/iris-jira-status.json | jq . || cat /tmp/iris-jira-status.json

demo_flow \
  "Scenario 1 · Employee onboarding with incomplete input" \
  "mert.kaya@acme.io" \
  '{
    "workItemType": "REQUEST",
    "originType": "USER_CREATED",
    "summary": "New employee onboarding",
    "description": "A new software developer will start on Monday. Prepare all required onboarding activities.",
    "departmentId": "00000000-0000-7000-8000-00000000a101",
    "priority": "HIGH"
  }'

demo_flow \
  "Scenario 2 · Development story with richer scope" \
  "ayse.demir@acme.io" \
  '{
    "workItemType": "TASK",
    "originType": "USER_CREATED",
    "summary": "Profile photo upload",
    "description": "Users must be able to upload a profile photo. Support JPG and PNG up to 5 MB, store originals in object storage, generate thumbnails, and preserve existing avatars during migration.",
    "departmentId": "00000000-0000-7000-8000-00000000a103",
    "priority": "MEDIUM"
  }'

demo_flow \
  "Scenario 3 · Production-sensitive payments incident" \
  "elif.yilmaz@acme.io" \
  '{
    "workItemType": "INCIDENT",
    "originType": "USER_CREATED",
    "summary": "Payments fail intermittently during checkout",
    "description": "Production customers intermittently fail checkout in the payments flow. Investigate root cause, ship a fix, and verify the incident is resolved.",
    "departmentId": "00000000-0000-7000-8000-00000000a103",
    "priority": "HIGH"
  }'

echo
echo "Demo smoke complete."
echo "Open the UI at http://localhost:5173 and continue with the matching accounts."
