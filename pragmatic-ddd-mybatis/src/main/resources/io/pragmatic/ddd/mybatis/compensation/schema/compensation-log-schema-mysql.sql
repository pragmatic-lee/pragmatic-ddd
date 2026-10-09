CREATE TABLE IF NOT EXISTS compensation_log (
    action_key   VARCHAR(255)  NOT NULL,
    handler      VARCHAR(255)  NOT NULL,
    payload      LONGTEXT,
    status       VARCHAR(20)   NOT NULL,
    attempts     INT           NOT NULL DEFAULT 0,
    claim_token  VARCHAR(64),
    claimed_at   DATETIME(3),
    last_error   VARCHAR(2000),
    created_at   DATETIME(3)   NOT NULL,
    updated_at   DATETIME(3)   NOT NULL,
    PRIMARY KEY (action_key),
    INDEX idx_compensation_status_updated (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '补偿日志表（WAL）：PENDING/EXECUTED/COMPENSATING/COMPENSATED/CONFIRMED/FAILED';
