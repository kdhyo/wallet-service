# 거래내역 조회 전환 설계안 (입금 API 미구현)

## 1) 목표
- 기존 `출금 거래내역 조회`를 `거래내역 조회`로 확장 가능한 구조로 전환한다.
- 현재 릴리즈에서는 **출금만 처리**하며, 입금 API/서비스는 구현하지 않는다.
- 거래 조회에서는 `PROCESSING`을 제외하고 **완료 상태(`SUCCESS`, `FAILED`)만 반환**한다.
- `transaction_id`는 전역 유니크로 유지한다.

## 2) 범위
- 포함
1. 테이블/엔티티를 `wallet_transaction` 기반으로 일반화
2. 출금 서비스의 멱등성/동시성 유지
3. 거래내역 조회 API 네이밍/응답 구조 일반화
4. 상태 기반 조회 검증 테스트
- 제외
1. 입금 API, 입금 비즈니스 로직
2. DEPOSIT 데이터 생성 시나리오 테스트

## 3) 데이터 모델

### wallet
- `id BIGINT AUTO_INCREMENT PK`
- `public_wallet_id VARCHAR(30) UNIQUE NOT NULL`
- `balance DECIMAL(19,4) NOT NULL CHECK (balance >= 0)`
- `created_at`, `updated_at`

### wallet_transaction
- `id BIGINT AUTO_INCREMENT PK`
- `transaction_id VARCHAR(128) UNIQUE NOT NULL`
- `wallet_id BIGINT NOT NULL FK -> wallet.id`
- `transaction_type VARCHAR(16) NOT NULL` (`DEPOSIT`, `WITHDRAW`)
- `amount DECIMAL(19,4) NOT NULL CHECK (amount > 0)`
- `balance_after DECIMAL(19,4) NULL`
- `status VARCHAR(16) NOT NULL` (`PROCESSING`, `SUCCESS`, `FAILED`)
- `error_code VARCHAR(64) NULL`
- `processed_at DATETIME(6) NULL`
- `created_at`, `updated_at`

### 상태 제약 정책
- `PROCESSING`: `WITHDRAW`에서만 허용, `balance_after/error_code/processed_at`는 `NULL`
- `SUCCESS`: `balance_after/processed_at` 필수, `error_code`는 `NULL`
- `FAILED`: `WITHDRAW`에서만 허용, `balance_after/error_code/processed_at` 필수

### 인덱스
- `(wallet_id, id DESC)` 커서 페이지 조회용

## 4) API 설계

### 출금 API (유지)
- `POST /api/v1/wallets/{walletId}/withdrawals`
- 요청: `transaction_id`, `amount`
- 응답: `transaction_id`, `wallet_id`, `amount`, `balance_after`, `status`, `error_code`, `processed_at`
- 규칙
1. `transaction_id` 중복 시 멱등 재생 처리
2. 동일 `transaction_id` + 다른 payload면 `409 멱등키_요청불일치`
3. 잔액 부족 시 `409 잔액_부족`

### 거래내역 조회 API (변경)
- `GET /api/v1/wallets/{walletId}/transactions?limit=20&cursor={id}`
- 응답 항목
1. `transaction_id`
2. `wallet_id`
3. `transaction_type`
4. `amount`
5. `balance`
6. `transaction_date`
7. `status`
8. `error_code`
- 페이지네이션
1. 정렬: `id DESC`
2. 커서: 마지막 행의 `id`
3. 쿼리: `id < :cursor` + `limit + 1` 조회

## 5) 출금 처리 순서
1. `walletId(public_wallet_id)`로 `wallet` 행을 `PESSIMISTIC_WRITE`로 잠금
2. 내부 `wallet.id` 획득
3. `wallet_transaction`에 `PROCESSING/WITHDRAW` INSERT 시도
4. `DuplicateKeyException(1062)`면 기존 거래 조회 후 멱등 재생
5. 신규 등록 요청이면 잔액 검사
6. 성공: 월렛 차감 + 거래 `SUCCESS`
7. 실패: 거래 `FAILED(INSUFFICIENT_BALANCE)` 확정 후 예외 반환
8. 트랜잭션 커밋

> MySQL + FK 환경에서는 거래 row를 먼저 INSERT하면 부모(wallet) 행에 공유 잠금이 잡혀,
> 이후 `FOR UPDATE`로 잠금 승격 시 동시 요청에서 교착(Deadlock) 가능성이 커진다.
> 그래서 본 구현은 wallet 잠금을 먼저 획득한다.

## 6) 조회 정책
- 조회 API는 완료 상태만 노출:
1. `SUCCESS`
2. `FAILED`
- `PROCESSING`은 운영상 내부 중간 상태이며 외부 조회에서 제외한다.

## 7) 테스트 전략

### 서비스 통합 테스트
1. 동일 `transaction_id` 동시 100건: 1회만 반영, 나머지 멱등 성공
2. 동일 `transaction_id` 다른 payload: 409
3. 서로 다른 `transaction_id` 동시 출금: 잔액 음수 미발생, 총 출금액 초과 없음
4. 잔액 부족 재시도: 동일 실패 재현
5. 커서 페이지: `hasNext/nextCursor` 일관성
6. 조회 상태 검증: `SUCCESS/FAILED`만 반환, `PROCESSING` 제외

### API 통합 테스트
1. 100건 동시 출금 요청 시 `200/409`만 반환
2. DB 최종 잔액/성공 합계 정합성 검증

### 컨트롤러 검증 테스트
1. `amount <= 0` 검증
2. `limit` 범위 검증
