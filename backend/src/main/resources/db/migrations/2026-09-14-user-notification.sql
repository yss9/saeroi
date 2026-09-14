CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target_path VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_notification PRIMARY KEY (id),
    CONSTRAINT uk_user_notification_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_user_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    INDEX idx_user_notification_recipient_created (recipient_id, created_at, id),
    INDEX idx_user_notification_recipient_read (recipient_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
