-- Manual MySQL 8 migration. Apply once, before starting the new application.
-- Existing requests may have no unambiguous selected processor: never guess that party.
-- Keep their processor_id NULL until an operator backfills it from confirmed business records.
-- After backfill: ALTER TABLE processing_request MODIFY processor_id BIGINT NOT NULL;
ALTER TABLE processing_request
    ADD COLUMN processor_id BIGINT NULL AFTER brand_id,
    MODIFY COLUMN status ENUM('QUOTING', 'SELECTED', 'CANCELED', 'REJECTED', 'EXPIRED') NOT NULL DEFAULT 'QUOTING',
    ADD CONSTRAINT fk_processing_request_processor FOREIGN KEY (processor_id) REFERENCES users (id),
    ADD CONSTRAINT chk_processing_request_parties CHECK (brand_id <> processor_id),
    ADD INDEX idx_processing_request_processor_status (processor_id, status);

ALTER TABLE material_lock
    MODIFY COLUMN reference_type ENUM('LISTING', 'PROCESSING_REQUEST', 'PROCESSING_JOB', 'PRODUCTION_BATCH') NOT NULL;

-- Check and reconcile any legacy duplicate inputs/purposes before these constraints.
ALTER TABLE processing_request_input
    ADD CONSTRAINT uk_processing_request_input_material UNIQUE (processing_request_id, material_id);
ALTER TABLE processing_request_type
    ADD CONSTRAINT uk_processing_request_type_purpose UNIQUE (processing_request_id, process_type);

-- Existing QUOTING requests are not automatically reserved or assigned a new seven-day deadline.
-- Historical reconciliation is separate from new request creation; this migration does not invent locks.
