CREATE TABLE wallet
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_wallet_id VARCHAR(30)    NOT NULL UNIQUE,
    balance          DECIMAL(19, 4) NOT NULL CHECK (balance >= 0),
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE wallet_transaction
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id   VARCHAR(128)   NOT NULL UNIQUE,
    wallet_id        BIGINT         NOT NULL,
    transaction_type VARCHAR(16)    NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAW')),
    amount           DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    balance_after    DECIMAL(19, 4),
    status           VARCHAR(16)    NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED')),
    error_code       VARCHAR(64),
    processed_at     DATETIME(6),
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_wallet_transaction_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT chk_wallet_transaction_state
        CHECK (
            (
                status = 'PROCESSING' AND
                balance_after IS NULL AND
                error_code IS NULL AND
                processed_at IS NULL
                )
                OR
            (
                status = 'SUCCESS' AND
                balance_after IS NOT NULL AND
                balance_after >= 0 AND
                error_code IS NULL AND
                processed_at IS NOT NULL
                )
                OR
            (
                status = 'FAILED' AND
                balance_after IS NOT NULL AND
                balance_after >= 0 AND
                error_code IS NOT NULL AND
                processed_at IS NOT NULL
                )
            )
);

CREATE INDEX idx_wallet_transaction_wallet_cursor
    ON wallet_transaction (wallet_id, id DESC);
