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

-- MySQL 8.4는 외래키에 참여한 컬럼을 FK가 살아 있는 상태에서 이름 변경하지 못한다.
-- 기존 FK를 먼저 해제하고, 컬럼과 인덱스 이름을 바꾼 뒤 양쪽 Lock FK를 다시 연결한다.
ALTER TABLE processing_input
    DROP FOREIGN KEY fk_processing_input_lock;

ALTER TABLE processing_input
    CHANGE COLUMN material_lock_id brand_material_lock_id BIGINT NOT NULL,
    RENAME INDEX fk_processing_input_lock TO fk_processing_input_brand_lock,
    ADD COLUMN processor_material_lock_id BIGINT NULL AFTER brand_material_lock_id,
    ADD CONSTRAINT fk_processing_input_brand_lock
        FOREIGN KEY (brand_material_lock_id) REFERENCES material_lock (id),
    ADD CONSTRAINT fk_processing_input_processor_lock
        FOREIGN KEY (processor_material_lock_id) REFERENCES material_lock (id);

ALTER TABLE processing_job
    MODIFY COLUMN status ENUM(
        'CREATED','PAID','SHIPPED','RECEIVED','PROCESSING','RESULT_SUBMITTED',
        'RETURN_SHIPPED','CONFIRMED','REJECTED','EXPIRED'
    ) NOT NULL DEFAULT 'CREATED';
