ALTER TABLE processing_job
    ADD COLUMN carrier VARCHAR(50) NULL AFTER payment_expires_at,
    ADD COLUMN tracking_number VARCHAR(100) NULL AFTER carrier,
    ADD COLUMN result_revision INT NOT NULL DEFAULT 0 AFTER tracking_number,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER result_revision,
    ADD COLUMN result_submit_signature VARCHAR(132) NULL AFTER rejection_reason,
    ADD COLUMN approval_signature VARCHAR(132) NULL AFTER result_submit_signature,
    ADD COLUMN rejection_signature VARCHAR(132) NULL AFTER approval_signature,
    ADD COLUMN started_at DATETIME NULL AFTER received_at,
    ADD COLUMN result_drafted_at DATETIME NULL AFTER started_at,
    ADD COLUMN result_submitted_at DATETIME NULL AFTER result_drafted_at,
    ADD COLUMN rejected_at DATETIME NULL AFTER result_submitted_at;

ALTER TABLE processing_output
    DROP CHECK chk_processing_output_quantity,
    ADD CONSTRAINT chk_processing_output_quantity CHECK (quantity > 0);

CREATE TABLE processing_signature_challenge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_job_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action ENUM('RESULT_SUBMIT', 'APPROVE', 'REJECT') NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    result_revision INT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    nonce CHAR(32) NOT NULL,
    message TEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    CONSTRAINT pk_processing_signature_challenge PRIMARY KEY (id),
    CONSTRAINT fk_processing_signature_job FOREIGN KEY (processing_job_id) REFERENCES processing_job (id),
    CONSTRAINT fk_processing_signature_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT chk_processing_signature_chain CHECK (chain_id > 0),
    CONSTRAINT chk_processing_signature_revision CHECK (result_revision > 0),
    INDEX idx_processing_signature_job_actor_action (processing_job_id, actor_id, action, used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
