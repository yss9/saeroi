ALTER TABLE processing_job
    ADD COLUMN chain_job_id BIGINT NULL AFTER processor_wallet,
    ADD COLUMN receive_tx_hash CHAR(66) NULL AFTER tracking_number,
    ADD COLUMN receive_block_number BIGINT NULL AFTER receive_tx_hash,
    ADD COLUMN return_carrier VARCHAR(50) NULL AFTER receive_block_number,
    ADD COLUMN return_tracking_number VARCHAR(100) NULL AFTER return_carrier,
    ADD COLUMN return_shipping_evidence_hash CHAR(66) NULL AFTER return_tracking_number,
    ADD COLUMN return_ship_tx_hash CHAR(66) NULL AFTER return_tracking_number,
    ADD COLUMN return_ship_block_number BIGINT NULL AFTER return_ship_tx_hash,
    ADD COLUMN return_receive_tx_hash CHAR(66) NULL AFTER return_ship_block_number,
    ADD COLUMN return_receive_block_number BIGINT NULL AFTER return_receive_tx_hash,
    ADD COLUMN return_shipped_at DATETIME NULL AFTER rejected_at,
    ADD CONSTRAINT uk_processing_job_chain UNIQUE (chain_job_id),
    ADD CONSTRAINT uk_processing_job_receive_tx UNIQUE (receive_tx_hash),
    ADD CONSTRAINT uk_processing_job_return_ship_tx UNIQUE (return_ship_tx_hash),
    ADD CONSTRAINT uk_processing_job_return_receive_tx UNIQUE (return_receive_tx_hash);

ALTER TABLE processing_input
    CHANGE COLUMN material_lock_id brand_material_lock_id BIGINT NOT NULL,
    ADD COLUMN processor_material_lock_id BIGINT NULL AFTER brand_material_lock_id,
    ADD CONSTRAINT fk_processing_input_processor_lock
        FOREIGN KEY (processor_material_lock_id) REFERENCES material_lock (id);

ALTER TABLE processing_job
    MODIFY COLUMN status ENUM(
        'CREATED','PAID','SHIPPED','RECEIVED','PROCESSING','RESULT_SUBMITTED',
        'RETURN_SHIPPED','CONFIRMED','REJECTED','EXPIRED'
    ) NOT NULL DEFAULT 'CREATED';
