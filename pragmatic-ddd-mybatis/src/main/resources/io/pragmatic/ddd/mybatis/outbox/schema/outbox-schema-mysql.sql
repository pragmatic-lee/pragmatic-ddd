CREATE TABLE IF NOT EXISTS ${outboxTable} (
    id             VARCHAR(36)   NOT NULL,
    aggregate_id   VARCHAR(128)  NOT NULL,
    aggregate_type VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(255)  NOT NULL,
    entity_id    VARCHAR(128),
    payload        LONGTEXT      NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    queue          INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(3)   NOT NULL,
    claimed_at     DATETIME(3),
    sent_at        DATETIME(3),
    last_error     VARCHAR(2000),
    claim_token    VARCHAR(36),
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_claim_token (claim_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
