#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
WALLET_ID="${WALLET_ID:-wlt_test_wallet_0009}"
AMOUNT="${AMOUNT:-10000}"
REQUEST_COUNT="${REQUEST_COUNT:-100}"
PARALLELISM="${PARALLELISM:-20}"
TX_PREFIX="${TX_PREFIX:-txn-load-$(date +%s)}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

if ! curl -sS -m 2 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "오류: ${BASE_URL} 에 API 서버가 떠있지 않습니다."
  echo "먼저 'docker compose up -d --build' 또는 './gradlew bootRun'으로 서버를 실행하세요."
  exit 1
fi

run_one() {
  local idx="$1"
  local tx_id="${TX_PREFIX}-${idx}"
  local body_file="${tmp_dir}/${idx}.json"
  local status_file="${tmp_dir}/${idx}.status"

  local status_code
  if status_code="$(
    curl -sS -m 5 -o "${body_file}" -w "%{http_code}" \
      -X POST "${BASE_URL}/api/v1/wallets/${WALLET_ID}/withdrawals" \
      -H "Content-Type: application/json" \
      -d "{\"transaction_id\":\"${tx_id}\",\"amount\":${AMOUNT}}"
  )"; then
    echo "${status_code}" > "${status_file}"
  else
    echo "CURL_ERROR" > "${status_file}"
  fi
}

export BASE_URL WALLET_ID AMOUNT TX_PREFIX tmp_dir
export -f run_one

echo "동시 요청 실행: request_count=${REQUEST_COUNT}, parallelism=${PARALLELISM}"
seq "${REQUEST_COUNT}" | xargs -P "${PARALLELISM}" -I{} bash -lc 'run_one "$@"' _ {} || true

shopt -s nullglob
status_files=("${tmp_dir}"/*.status)
if [[ ${#status_files[@]} -eq 0 ]]; then
  echo "오류: 요청 결과 파일이 생성되지 않았습니다."
  exit 1
fi

ok_count=0
conflict_count=0
other_count=0

for f in "${status_files[@]}"; do
  code="$(cat "${f}")"
  case "${code}" in
    200) ((ok_count+=1)) ;;
    409) ((conflict_count+=1)) ;;
    *) ((other_count+=1)) ;;
  esac
done

echo "결과 요약"
echo "- HTTP 200: ${ok_count}"
echo "- HTTP 409: ${conflict_count}"
echo "- 기타 코드: ${other_count}"

echo "최근 거래내역 5건"
curl -sS "${BASE_URL}/api/v1/wallets/${WALLET_ID}/transactions?limit=5"
echo
