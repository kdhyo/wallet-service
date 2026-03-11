#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
WALLET_ID="${WALLET_ID:-wlt_test_wallet_0010}"
AMOUNT="${AMOUNT:-10000}"
TX_ID="${TX_ID:-txn-smoke-$(date +%s)}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

post_withdraw() {
  local tx_id="$1"
  local amount="$2"
  local response_file="$3"

  curl -sS -o "${response_file}" -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/wallets/${WALLET_ID}/withdrawals" \
    -H "Content-Type: application/json" \
    -d "{\"transaction_id\":\"${tx_id}\",\"amount\":${amount}}"
}

echo "[1/3] 첫 출금 요청"
first_status="$(post_withdraw "${TX_ID}" "${AMOUNT}" "${tmp_dir}/first.json")"
echo "HTTP ${first_status}"
cat "${tmp_dir}/first.json"
echo

echo "[2/3] 동일 transaction_id 재요청 (멱등성 확인)"
second_status="$(post_withdraw "${TX_ID}" "${AMOUNT}" "${tmp_dir}/second.json")"
echo "HTTP ${second_status}"
cat "${tmp_dir}/second.json"
echo

echo "[3/3] 거래내역 조회"
curl -sS \
  "${BASE_URL}/api/v1/wallets/${WALLET_ID}/transactions?limit=5"
echo

echo "완료: BASE_URL=${BASE_URL}, wallet_id=${WALLET_ID}, transaction_id=${TX_ID}"
