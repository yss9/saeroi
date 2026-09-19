-- Upcycle MySQL 8 schema derived from DB_설계.md (DB 설계 v1).
-- Run this script against the database selected by the connection.
--
-- Conventions
--   - All material quantities are stored as gram BIGINT integers.
--   - All monetary amounts are stored as KRW BIGINT integers.
--   - On-chain values (material id, balance, lock, listing, trade) live on the
--     blockchain. The matching tables here are read-optimised projections.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    role ENUM('SUPPLIER', 'BRAND', 'PROCESSOR', 'CONSUMER', 'ADMIN') NOT NULL,
    status ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 지갑은 계정당 활성 1개이고, 교체하면 이전 행을 비활성으로 남긴다.
--
-- user_id 에 UNIQUE 를 걸지 않는 이유: 교체할 때 행을 UPDATE 하거나 지우면 이전 주소가
-- 사라진다. material_trade.seller_wallet / processing_job.processor_wallet /
-- production_batch.producer_wallet 등 6개 컬럼이 서명 시점 주소를 스냅샷으로 들고 있어서,
-- 그 주소가 누구 것이었는지 역추적할 수 없게 된다.
--
-- 활성 단일성은 생성 컬럼으로 강제한다. MySQL 8 은 부분 UNIQUE(WHERE 절)를 지원하지 않지만,
-- 비활성 행을 NULL 로 만들면 UNIQUE 에서 NULL 은 서로 충돌하지 않으므로 활성만 1개로 제한된다.
-- IF() 는 H2(테스트)가 파싱하지 못하므로 표준 CASE WHEN 을 쓴다.
CREATE TABLE user_wallet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at DATETIME NULL,
    active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active THEN user_id ELSE NULL END),
    CONSTRAINT pk_user_wallet PRIMARY KEY (id),
    CONSTRAINT uk_user_wallet_active_user UNIQUE (active_user_id),
    CONSTRAINT uk_user_wallet_address UNIQUE (wallet_address),
    CONSTRAINT fk_user_wallet_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_wallet_chain_id CHECK (chain_id > 0),
    INDEX idx_user_wallet_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_nonce (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    nonce CHAR(32) NOT NULL,
    chain_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallet_nonce PRIMARY KEY (id),
    CONSTRAINT uk_wallet_nonce_nonce UNIQUE (nonce),
    CONSTRAINT fk_wallet_nonce_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wallet_nonce_chain_id CHECK (chain_id > 0),
    INDEX idx_wallet_nonce_user (user_id),
    INDEX idx_wallet_nonce_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company_profile (
    user_id BIGINT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    business_number VARCHAR(50) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    introduction TEXT NULL,
    logo_url VARCHAR(500) NULL,
    role_approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    role_approved_wallet VARCHAR(42) NULL,
    role_approval_tx_hashes TEXT NULL,
    role_reviewed_at DATETIME NULL,
    role_reviewed_by BIGINT NULL,
    CONSTRAINT pk_company_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_company_profile_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_company_profile_role_reviewer FOREIGN KEY (role_reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_company_profile_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_company_profile_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_company_profile_coordinates_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consumer_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    `alias` VARCHAR(100) NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_consumer_address PRIMARY KEY (id),
    CONSTRAINT fk_consumer_address_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_consumer_address_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_refresh_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Processor -----------------------------------------------------------------

CREATE TABLE processor_profile (
    user_id BIGINT NOT NULL,
    region VARCHAR(100) NOT NULL,
    CONSTRAINT pk_processor_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_processor_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processor_capability (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processor_profile_id BIGINT NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    process_type ENUM(
        'CLEANING',
        'DISASSEMBLY',
        'SORTING',
        'CUTTING',
        'SHREDDING',
        'COLOR_PROCESSING',
        'SURFACE_FINISHING',
        'REMANUFACTURING'
    ) NOT NULL,
    min_quantity BIGINT NOT NULL,
    max_quantity BIGINT NOT NULL,
    CONSTRAINT pk_processor_capability PRIMARY KEY (id),
    CONSTRAINT uk_processor_capability UNIQUE (
        processor_profile_id,
        material_type,
        process_type
    ),
    CONSTRAINT fk_processor_capability_profile FOREIGN KEY (processor_profile_id)
        REFERENCES processor_profile (user_id),
    CONSTRAINT chk_processor_capability_quantity CHECK (
        min_quantity > 0 AND max_quantity >= min_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processor_lead_time (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processor_profile_id BIGINT NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    process_type ENUM(
        'CLEANING',
        'DISASSEMBLY',
        'SORTING',
        'CUTTING',
        'SHREDDING',
        'COLOR_PROCESSING',
        'SURFACE_FINISHING',
        'REMANUFACTURING'
    ) NOT NULL,
    max_quantity BIGINT NOT NULL,
    lead_days INT NOT NULL,
    CONSTRAINT pk_processor_lead_time PRIMARY KEY (id),
    CONSTRAINT fk_processor_lead_time_profile FOREIGN KEY (processor_profile_id)
        REFERENCES processor_profile (user_id),
    CONSTRAINT uk_processor_lead_time UNIQUE (
        processor_profile_id,
        material_type,
        process_type,
        max_quantity
    ),
    CONSTRAINT chk_processor_lead_time_values CHECK (max_quantity > 0 AND lead_days > 0),
    INDEX idx_processor_lead_time_lookup (
        processor_profile_id,
        material_type,
        process_type,
        max_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Material ------------------------------------------------------------------

CREATE TABLE material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_code VARCHAR(20) NOT NULL,
    token_id BIGINT NULL,
    chain_id BIGINT NULL,
    contract_address VARCHAR(42) NULL,
    material_type VARCHAR(50) NOT NULL,
    root_origin ENUM('VIRGIN', 'WASTE') NOT NULL,
    generation_type ENUM('INITIAL', 'PROCESSED', 'PRODUCTION_SCRAP') NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    grade VARCHAR(50) NULL,
    total_quantity BIGINT NOT NULL,
    length_mm BIGINT NULL,
    width_mm BIGINT NULL,
    registrant_id BIGINT NOT NULL,
    -- 왜 폐기물이 되었나. generation_type("어떻게 생겼나")과 역할이 다르므로
    -- PRODUCTION_SCRAP 을 여기 넣지 않는다 — 같은 사실을 두 컬럼에 적으면 어긋난다.
    -- root_origin='WASTE' 일 때만 필수이고 그 검사는 서비스가 한다.
    origin_reason ENUM(
        'DEADSTOCK',
        'DEFECT',
        'OFFCUT',
        'END_OF_LIFE',
        'COLLECTED',
        'ETC'
    ) NULL,
    origin_reason_note VARCHAR(200) NULL,
    evidence_hash CHAR(66) NULL,
    manifest_url VARCHAR(500) NULL,
    status ENUM('ACTIVE', 'DEPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_material PRIMARY KEY (id),
    CONSTRAINT uk_material_code UNIQUE (material_code),
    CONSTRAINT uk_material_token_id UNIQUE (token_id),
    CONSTRAINT fk_material_registrant FOREIGN KEY (registrant_id) REFERENCES users (id),
    CONSTRAINT chk_material_quantity CHECK (total_quantity >= 0),
    CONSTRAINT chk_material_chain_id CHECK (chain_id IS NULL OR chain_id > 0),
    CONSTRAINT chk_material_dimensions CHECK (
        (length_mm IS NULL OR length_mm > 0)
        AND (width_mm IS NULL OR width_mm > 0)
    ),
    INDEX idx_material_type_origin (material_type, root_origin),
    INDEX idx_material_registrant (registrant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    quantity BIGINT NOT NULL,
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_material_balance PRIMARY KEY (id),
    CONSTRAINT uk_material_balance_owner UNIQUE (material_id, owner_id),
    CONSTRAINT fk_material_balance_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_balance_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_material_balance_quantity CHECK (quantity >= 0),
    INDEX idx_material_balance_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_lock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_lock_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    purpose ENUM('MARKETPLACE', 'PROCESSING', 'PRODUCTION') NOT NULL,
    reference_type ENUM('LISTING', 'PROCESSING_REQUEST', 'PROCESSING_JOB', 'PRODUCTION_BATCH') NOT NULL,
    reference_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'RELEASED', 'CONSUMED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at DATETIME NULL,
    CONSTRAINT pk_material_lock PRIMARY KEY (id),
    CONSTRAINT uk_material_lock_chain_lock_id UNIQUE (chain_lock_id),
    CONSTRAINT fk_material_lock_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_lock_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_material_lock_amount CHECK (amount > 0),
    INDEX idx_material_lock_available (material_id, owner_id, status),
    INDEX idx_material_lock_reference (reference_type, reference_id),
    INDEX idx_material_lock_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_lineage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    child_material_id BIGINT NOT NULL,
    parent_material_id BIGINT NOT NULL,
    relation_type ENUM('PROCESSING', 'PRODUCTION_SCRAP') NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_material_lineage PRIMARY KEY (id),
    CONSTRAINT uk_material_lineage_edge UNIQUE (
        child_material_id,
        parent_material_id,
        reference_id
    ),
    CONSTRAINT fk_material_lineage_child FOREIGN KEY (child_material_id) REFERENCES material (id),
    CONSTRAINT fk_material_lineage_parent FOREIGN KEY (parent_material_id) REFERENCES material (id),
    CONSTRAINT chk_material_lineage_no_self CHECK (child_material_id <> parent_material_id),
    INDEX idx_material_lineage_child (child_material_id),
    INDEX idx_material_lineage_parent (parent_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Material event journal. 기존 도메인 테이블을 대체하지 않는 append-only 조회·감사 기록이다.
CREATE TABLE material_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    actor_id BIGINT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    related_material_id BIGINT NULL,
    quantity BIGINT NULL,
    detail VARCHAR(500) NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    occurred_at DATETIME NOT NULL,
    CONSTRAINT pk_material_event PRIMARY KEY (id),
    CONSTRAINT uk_material_event_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_material_event_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_event_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_material_event_related_material
        FOREIGN KEY (related_material_id) REFERENCES material (id),
    CONSTRAINT chk_material_event_quantity CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT chk_material_event_reference CHECK (
        (reference_type IS NULL AND reference_id IS NULL)
        OR (reference_type IS NOT NULL AND reference_id IS NOT NULL)
    ),
    INDEX idx_material_event_timeline (material_id, occurred_at, id),
    INDEX idx_material_event_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_composition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    fiber_type VARCHAR(50) NOT NULL,
    percentage DECIMAL(5, 2) NOT NULL,
    CONSTRAINT pk_material_composition PRIMARY KEY (id),
    CONSTRAINT uk_material_composition_fiber UNIQUE (material_id, fiber_type),
    CONSTRAINT fk_material_composition_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT chk_material_composition_percentage CHECK (percentage > 0 AND percentage <= 100),
    INDEX idx_material_composition_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 합계는 CHECK 로 표현되지 않는다 (여러 행에 걸친 조건). 서비스가 100 이하만 확인하고
-- 정확히 100 을 요구하지 않는다 — 요구하면 모르는 부분을 지어내게 된다.

-- Evidence ------------------------------------------------------------------

CREATE TABLE evidence_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_type ENUM(
        'MATERIAL',
        'PROCESSING_JOB',
        'PRODUCTION_BATCH',
        'PRODUCTION_INPUT'
    ) NOT NULL,
    reference_id BIGINT NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_hash CHAR(66) NOT NULL,
    evidence_type ENUM(
        'BEFORE_PHOTO',
        'AFTER_PHOTO',
        'WEIGHT_SLIP',
        'WEIGHT_PHOTO',
        'INVOICE',
        'PROCESS_PHOTO',
        'ETC'
    ) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 소프트 삭제. 행은 남는다 (S15P21E104-162).
    -- file_hash 가 온체인 manifest 에 들어갈 수 있어, 행을 지우면 체인에는 해시가 남아
    -- 있는데 무엇의 해시인지 알 수 없는 상태가 된다. 그건 되돌릴 수 없다.
    -- 저장소의 바이트는 하드 삭제한다 — 사용자가 원하는 것은 파일이 없어지는 것이다.
    -- deleted_by 를 따로 두는 이유: 증빙이 사라진 건에서 감사가 묻는 것은 "누가 지웠나"
    -- 이고, uploaded_by 로는 답할 수 없다 (등록자도 지울 수 있다).
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT pk_evidence_file PRIMARY KEY (id),
    CONSTRAINT fk_evidence_file_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT fk_evidence_file_deleter FOREIGN KEY (deleted_by) REFERENCES users (id),
    -- 삭제 여부가 조건에 항상 붙는다 (목록은 살아 있는 것만 본다). 선행 두 컬럼만으로도
    -- 인덱스를 타지만, deleted_at 을 뒤에 붙여 두면 그 필터까지 인덱스에서 끝난다.
    INDEX idx_evidence_file_reference (reference_type, reference_id, deleted_at),
    INDEX idx_evidence_file_uploader (uploaded_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Processing ----------------------------------------------------------------

CREATE TABLE processing_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_code VARCHAR(30) NOT NULL,
    brand_id BIGINT NOT NULL,
    processor_id BIGINT NOT NULL,
    status ENUM('QUOTING', 'SELECTED', 'CANCELED', 'REJECTED', 'EXPIRED') NOT NULL DEFAULT 'QUOTING',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_processing_request PRIMARY KEY (id),
    CONSTRAINT uk_processing_request_code UNIQUE (request_code),
    CONSTRAINT fk_processing_request_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT fk_processing_request_processor FOREIGN KEY (processor_id) REFERENCES users (id),
    CONSTRAINT chk_processing_request_parties CHECK (brand_id <> processor_id),
    INDEX idx_processing_request_brand_status (brand_id, status),
    INDEX idx_processing_request_processor_status (processor_id, status),
    INDEX idx_processing_request_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_request_input (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_request_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_processing_request_input PRIMARY KEY (id),
    CONSTRAINT uk_processing_request_input_material UNIQUE (processing_request_id, material_id),
    CONSTRAINT fk_processing_request_input_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id),
    CONSTRAINT fk_processing_request_input_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT chk_processing_request_input_quantity CHECK (quantity > 0),
    INDEX idx_processing_request_input_request (processing_request_id),
    INDEX idx_processing_request_input_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_request_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_request_id BIGINT NOT NULL,
    process_type ENUM(
        'CLEANING',
        'DISASSEMBLY',
        'SORTING',
        'CUTTING',
        'SHREDDING',
        'COLOR_PROCESSING',
        'SURFACE_FINISHING',
        'REMANUFACTURING'
    ) NOT NULL,
    sequence_no INT NOT NULL,
    CONSTRAINT pk_processing_request_type PRIMARY KEY (id),
    CONSTRAINT uk_processing_request_type_purpose UNIQUE (processing_request_id, process_type),
    CONSTRAINT uk_processing_request_type_sequence UNIQUE (processing_request_id, sequence_no),
    CONSTRAINT fk_processing_request_type_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id),
    CONSTRAINT chk_processing_request_type_sequence CHECK (sequence_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_quote (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_request_id BIGINT NOT NULL,
    processor_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    lead_days INT NOT NULL,
    memo TEXT NULL,
    status ENUM('SUBMITTED', 'SELECTED', 'REJECTED', 'EXPIRED') NOT NULL DEFAULT 'SUBMITTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_processing_quote PRIMARY KEY (id),
    CONSTRAINT uk_processing_quote_request_processor UNIQUE (processing_request_id, processor_id),
    CONSTRAINT fk_processing_quote_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id),
    CONSTRAINT fk_processing_quote_processor FOREIGN KEY (processor_id) REFERENCES users (id),
    CONSTRAINT chk_processing_quote_values CHECK (price >= 0 AND lead_days > 0),
    INDEX idx_processing_quote_request_status (processing_request_id, status),
    INDEX idx_processing_quote_processor (processor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_code VARCHAR(30) NOT NULL,
    processing_request_id BIGINT NOT NULL,
    processing_quote_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    processor_id BIGINT NOT NULL,
    brand_wallet VARCHAR(42) NOT NULL,
    processor_wallet VARCHAR(42) NOT NULL,
    chain_job_id BIGINT NULL,
    fee BIGINT NOT NULL,
    loss_quantity BIGINT NOT NULL DEFAULT 0,
    evidence_hash CHAR(66) NULL,
    manifest_url VARCHAR(500) NULL,
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_amount BIGINT NOT NULL DEFAULT 0,
    status ENUM(
        'CREATED',
        'PAID',
        'SHIPPED',
        'RECEIVED',
        'PROCESSING',
        'RESULT_SUBMITTED',
        'RETURN_SHIPPED',
        'CONFIRMED',
        'REJECTED',
        'EXPIRED',
        'CANCELED'
    ) NOT NULL DEFAULT 'CREATED',
    payment_expires_at DATETIME NOT NULL,
    carrier VARCHAR(50) NULL,
    tracking_number VARCHAR(100) NULL,
    receive_tx_hash CHAR(66) NULL,
    receive_block_number BIGINT NULL,
    return_carrier VARCHAR(50) NULL,
    return_tracking_number VARCHAR(100) NULL,
    return_shipping_evidence_hash CHAR(66) NULL,
    return_ship_tx_hash CHAR(66) NULL,
    return_ship_block_number BIGINT NULL,
    return_receive_tx_hash CHAR(66) NULL,
    return_receive_block_number BIGINT NULL,
    result_revision INT NOT NULL DEFAULT 0,
    rejection_reason VARCHAR(500) NULL,
    result_submit_signature VARCHAR(132) NULL,
    approval_signature VARCHAR(132) NULL,
    rejection_signature VARCHAR(132) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at DATETIME NULL,
    received_at DATETIME NULL,
    started_at DATETIME NULL,
    result_drafted_at DATETIME NULL,
    result_submitted_at DATETIME NULL,
    rejected_at DATETIME NULL,
    return_shipped_at DATETIME NULL,
    completed_at DATETIME NULL,
    CONSTRAINT pk_processing_job PRIMARY KEY (id),
    CONSTRAINT uk_processing_job_code UNIQUE (job_code),
    CONSTRAINT uk_processing_job_quote UNIQUE (processing_quote_id),
    CONSTRAINT uk_processing_job_chain UNIQUE (chain_job_id),
    CONSTRAINT uk_processing_job_receive_tx UNIQUE (receive_tx_hash),
    CONSTRAINT uk_processing_job_return_ship_tx UNIQUE (return_ship_tx_hash),
    CONSTRAINT uk_processing_job_return_receive_tx UNIQUE (return_receive_tx_hash),
    CONSTRAINT fk_processing_job_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id),
    CONSTRAINT fk_processing_job_quote FOREIGN KEY (processing_quote_id) REFERENCES processing_quote (id),
    CONSTRAINT fk_processing_job_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT fk_processing_job_processor FOREIGN KEY (processor_id) REFERENCES users (id),
    CONSTRAINT chk_processing_job_amounts CHECK (
        fee >= 0 AND loss_quantity >= 0 AND payment_amount >= 0
    ),
    CONSTRAINT chk_processing_job_parties CHECK (brand_id <> processor_id),
    INDEX idx_processing_job_processor_status (processor_id, status),
    INDEX idx_processing_job_brand_status (brand_id, status),
    INDEX idx_processing_job_status_payment_expiry (status, payment_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_input (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_job_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    brand_material_lock_id BIGINT NOT NULL,
    processor_material_lock_id BIGINT NULL,
    CONSTRAINT pk_processing_input PRIMARY KEY (id),
    CONSTRAINT uk_processing_input_job_input UNIQUE (processing_job_id, id),
    CONSTRAINT fk_processing_input_job FOREIGN KEY (processing_job_id) REFERENCES processing_job (id),
    CONSTRAINT fk_processing_input_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_processing_input_brand_lock FOREIGN KEY (brand_material_lock_id) REFERENCES material_lock (id),
    CONSTRAINT fk_processing_input_processor_lock FOREIGN KEY (processor_material_lock_id) REFERENCES material_lock (id),
    CONSTRAINT chk_processing_input_quantity CHECK (quantity > 0),
    INDEX idx_processing_input_job (processing_job_id),
    INDEX idx_processing_input_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_output (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_job_id BIGINT NOT NULL,
    processing_input_id BIGINT NOT NULL,
    output_material_id BIGINT NULL,
    material_type VARCHAR(50) NOT NULL,
    grade VARCHAR(50) NOT NULL,
    grade_reason VARCHAR(500) NOT NULL,
    name VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_processing_output PRIMARY KEY (id),
    CONSTRAINT fk_processing_output_job_input FOREIGN KEY (processing_job_id, processing_input_id)
        REFERENCES processing_input (processing_job_id, id),
    CONSTRAINT fk_processing_output_material FOREIGN KEY (output_material_id) REFERENCES material (id),
    CONSTRAINT chk_processing_output_quantity CHECK (quantity > 0),
    INDEX idx_processing_output_job_input (processing_job_id, processing_input_id),
    INDEX idx_processing_output_input (processing_input_id),
    INDEX idx_processing_output_material (output_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

-- Product model / Production ------------------------------------------------

CREATE TABLE product_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    -- 소비자 SHOP 의 2단 메뉴. CLOTHING·BAG 에만 있고 나머지 분류와 하위 분류가 생기기
    -- 전에 등록된 제품은 NULL 이다 (ProductSubcategory 주석 참고).
    subcategory VARCHAR(50) NULL,
    brand_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    description TEXT NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    status ENUM('DRAFT', 'ON_SALE', 'SOLD_OUT', 'DISCONTINUED') NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_model PRIMARY KEY (id),
    CONSTRAINT fk_product_model_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_product_model_price CHECK (price >= 0),
    INDEX idx_product_model_brand_status (brand_id, status),
    -- 소비자 공개 목록은 언제나 status='ON_SALE' 로 시작해 분류로 좁힌다.
    -- 기존 인덱스는 brand_id 가 앞에 있어 브랜드를 고르지 않는 SHOP 질의에는 쓰이지 않는다.
    INDEX idx_product_model_status_category (status, category, subcategory)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_model_id BIGINT NOT NULL,
    -- FileStorageService 가 돌려준 저장 키. 화면에 붙일 주소가 아니다 —
    -- 바이트는 /api/v1/(public/)product-models/{id}/images/{imageId} 가 흘린다.
    -- product_model.thumbnail_url 과 같은 성격의 값이라 길이도 같다.
    storage_key VARCHAR(500) NOT NULL,
    image_type ENUM('MAIN', 'ANGLE', 'DETAIL', 'CONTEXT') NOT NULL,
    -- 갤러리 안에서의 자리. 0 부터 빈틈없이 이어진다 (교체 요청 배열의 순서).
    sort_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    -- 같은 제품의 두 사진이 같은 자리를 차지하면 어느 쪽이 먼저인지 답이 없다.
    -- 이 키가 제품별 조회 정렬도 함께 받친다.
    CONSTRAINT uk_product_image_model_order UNIQUE (product_model_id, sort_order),
    CONSTRAINT fk_product_image_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT chk_product_image_sort_order CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consumer_wishlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_consumer_wishlist PRIMARY KEY (id),
    -- 같은 상품을 두 번 담을 수 없다. 조회해 보고 넣는 것만으로는 같은 순간 들어온 두
    -- 요청이 함께 통과한다 - 하트 버튼은 연타되고 재시도도 흔해서 실제로 겹친다.
    -- 이 키가 "내가 찜했나" 조회와 찜 해제도 함께 받친다.
    CONSTRAINT uk_consumer_wishlist_user_product UNIQUE (user_id, product_model_id),
    CONSTRAINT fk_consumer_wishlist_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- 찜이 가리키는 동안에는 상품 행을 지울 수 없다. 이 저장소에 상품 삭제 경로는 없고
    -- 단종은 status 로 처리하지만, 운영에서 손으로 지우는 것까지 여기서 막는다.
    CONSTRAINT fk_consumer_wishlist_product FOREIGN KEY (product_model_id)
        REFERENCES product_model (id),
    -- 목록은 user_id 로 좁혀 담은 순서의 역순으로 읽는다. 위 유니크 키는 두 번째 열이
    -- product_model_id 라 그 정렬을 받치지 못한다.
    INDEX idx_consumer_wishlist_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consumer_cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    -- 이 항목을 마지막으로 담을 때 사용자가 본 가격. 결제 기준이 아니라 "담아 둔 사이에
    -- 값이 바뀌었다" 를 알리기 위한 값이다 (ConsumerCartItem.addedPrice 주석).
    added_price BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_consumer_cart_item PRIMARY KEY (id),
    -- 같은 상품이 두 행이 되면 목록에 같은 상품이 두 줄로 보인다. 조회 후 삽입만으로는
    -- 동시 요청이 함께 통과한다. 이 키가 목록 범위 스캔과 항목 조회·수정·삭제도 받친다.
    CONSTRAINT uk_consumer_cart_item_user_product UNIQUE (user_id, product_model_id),
    CONSTRAINT fk_consumer_cart_item_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- 담긴 동안에는 상품 행을 지울 수 없다. 찜과 같은 이유다.
    CONSTRAINT fk_consumer_cart_item_product FOREIGN KEY (product_model_id)
        REFERENCES product_model (id),
    CONSTRAINT chk_consumer_cart_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_consumer_cart_item_price CHECK (added_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- ProductionRegistry.createBatch 가 채번한 번호. 이후 submitPlanAndLock ·
    -- confirmProduction · linkProductItems 가 모두 이 값을 인자로 받는다.
    -- 서버가 들고 있지 않으면 화면이 네 단계 내내 그 번호를 붙들고 있어야 하고,
    -- 새로고침 한 번에 잃어버린다 (material.token_id · listing.chain_listing_id 와 같은 자리).
    chain_batch_id BIGINT NULL,
    batch_code VARCHAR(20) NOT NULL,
    batch_name VARCHAR(200) NOT NULL,
    -- 배치를 여는 시점에는 비어 있을 수 있다 — 소재를 고르기 전에 무엇을 만들지부터
    -- 확정하라고 강제할 이유가 없다. 확정 전에는 반드시 채워지며, 그 검사는 서비스가 한다.
    product_model_id BIGINT NULL,
    produced_quantity INT NOT NULL,
    producer_id BIGINT NOT NULL,
    producer_wallet VARCHAR(42) NOT NULL,
    status ENUM('DRAFT', 'SIGNING', 'CONFIRMED', 'FAILED', 'CANCELED') NOT NULL DEFAULT 'DRAFT',
    total_used_quantity BIGINT NOT NULL DEFAULT 0,
    virgin_used_quantity BIGINT NOT NULL DEFAULT 0,
    upcycled_used_quantity BIGINT NOT NULL DEFAULT 0,
    upcycle_ratio DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    evidence_hash CHAR(66) NULL,
    manifest_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    CONSTRAINT pk_production_batch PRIMARY KEY (id),
    CONSTRAINT uk_production_batch_code UNIQUE (batch_code),
    CONSTRAINT uk_production_batch_chain_id UNIQUE (chain_batch_id),
    CONSTRAINT fk_production_batch_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT fk_production_batch_producer FOREIGN KEY (producer_id) REFERENCES users (id),
    CONSTRAINT chk_production_batch_quantities CHECK (
        produced_quantity >= 0
        AND total_used_quantity >= 0
        AND virgin_used_quantity >= 0
        AND upcycled_used_quantity >= 0
        AND total_used_quantity = virgin_used_quantity + upcycled_used_quantity
    ),
    CONSTRAINT chk_production_batch_ratio CHECK (upcycle_ratio BETWEEN 0.00 AND 100.00),
    INDEX idx_production_batch_producer_status (producer_id, status),
    INDEX idx_production_batch_model (product_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_input (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_batch_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    input_quantity BIGINT NOT NULL,
    product_used_quantity BIGINT NOT NULL,
    reusable_scrap_quantity BIGINT NOT NULL,
    waste_quantity BIGINT NOT NULL,
    waste_reason VARCHAR(200) NULL,
    material_lock_id BIGINT NOT NULL,
    CONSTRAINT pk_production_input PRIMARY KEY (id),
    CONSTRAINT uk_production_input_batch_material UNIQUE (production_batch_id, material_id),
    CONSTRAINT fk_production_input_batch FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_production_input_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_production_input_lock FOREIGN KEY (material_lock_id) REFERENCES material_lock (id),
    CONSTRAINT chk_production_input_quantities CHECK (
        input_quantity > 0
        AND product_used_quantity >= 0
        AND reusable_scrap_quantity >= 0
        AND waste_quantity >= 0
        AND input_quantity = product_used_quantity + reusable_scrap_quantity + waste_quantity
    ),
    INDEX idx_production_input_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_output (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_input_id BIGINT NOT NULL,
    new_material_id BIGINT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_production_output PRIMARY KEY (id),
    CONSTRAINT fk_production_output_input FOREIGN KEY (production_input_id) REFERENCES production_input (id),
    CONSTRAINT fk_production_output_material FOREIGN KEY (new_material_id) REFERENCES material (id),
    CONSTRAINT chk_production_output_quantity CHECK (quantity > 0),
    INDEX idx_production_output_input (production_input_id),
    INDEX idx_production_output_material (new_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Product sales -------------------------------------------------------------

CREATE TABLE product_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_batch_id BIGINT NOT NULL,
    -- 확정 직후에는 비어 있을 수 있다 — 브랜드는 생산을 끝낸 뒤에 그 결과물에
    -- 제품 정보를 붙인다. 비어 있는 동안은 어느 모델의 재고로도 세지지 않는다.
    product_model_id BIGINT NULL,
    serial_number INT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    qr_token CHAR(32) NOT NULL,
    -- S15P21E104-141: 최초 생성된 QR PNG의 FileStorageService 저장 키. 요청마다 새로
    -- 렌더링하지 않고 한 번 저장한 이미지를 재사용한다. NULL이면 아직 저장된 적 없음(다음
    -- 조회 시점에 채워진다) — 기본 크기(DEFAULT_SIZE) 렌더만 캐시하고, 커스텀 size 요청은
    -- 캐시하지 않는다(크기별로 키가 갈리면 캐시 무효화 로직이 따로 필요해진다).
    qr_image_key VARCHAR(255) NULL,
    status ENUM('IN_STOCK', 'RESERVED', 'SOLD') NOT NULL DEFAULT 'IN_STOCK',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_item PRIMARY KEY (id),
    CONSTRAINT uk_product_item_code UNIQUE (item_code),
    CONSTRAINT uk_product_item_qr_token UNIQUE (qr_token),
    CONSTRAINT uk_product_item_batch_serial UNIQUE (production_batch_id, serial_number),
    CONSTRAINT fk_product_item_batch FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_product_item_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT chk_product_item_serial CHECK (serial_number > 0),
    INDEX idx_product_item_model_status (product_model_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_order_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_code VARCHAR(30) NOT NULL,
    buyer_id BIGINT NOT NULL,
    -- 묶음에 속한 주문들의 금액 합. 결제창에 청구하는 금액이 이 값이다.
    total_price BIGINT NOT NULL,
    status ENUM('CREATED', 'PAID') NOT NULL DEFAULT 'CREATED',
    -- 결제는 묶음 단위로 한 번만 한다. 주문마다 결제하면 브랜드가 셋인 장바구니에서
    -- 결제창이 세 번 뜬다. product_order 의 같은 이름 컬럼들은 단일 주문 경로가 계속 쓴다.
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_amount BIGINT NOT NULL DEFAULT 0,
    payment_deadline DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    CONSTRAINT pk_product_order_group PRIMARY KEY (id),
    CONSTRAINT uk_product_order_group_code UNIQUE (group_code),
    CONSTRAINT fk_product_order_group_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT chk_product_order_group_amounts CHECK (total_price >= 0 AND payment_amount >= 0),
    INDEX idx_product_order_group_buyer (buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(30) NOT NULL,
    buyer_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    -- 한 번의 결제로 함께 만들어진 주문들을 묶는다. 단일 주문(상품 상세의 "바로 구매")은
    -- NULL 이고 자기 결제 컬럼을 그대로 쓴다 - 그 경로를 건드리지 않기 위한 설계다.
    order_group_id BIGINT NULL,
    total_price BIGINT NOT NULL,
    status ENUM('CREATED', 'PAID', 'SHIPPED', 'DELIVERED') NOT NULL DEFAULT 'CREATED',
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_amount BIGINT NOT NULL DEFAULT 0,
    payment_deadline DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    shipped_at DATETIME NULL,
    delivered_at DATETIME NULL,
    CONSTRAINT pk_product_order PRIMARY KEY (id),
    CONSTRAINT uk_product_order_code UNIQUE (order_code),
    CONSTRAINT fk_product_order_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_product_order_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_product_order_amounts CHECK (total_price >= 0 AND payment_amount >= 0),
    CONSTRAINT fk_product_order_group FOREIGN KEY (order_group_id)
        REFERENCES product_order_group (id),
    INDEX idx_product_order_buyer_status (buyer_id, status),
    INDEX idx_product_order_brand_status (brand_id, status),
    -- 묶음에 속한 주문을 한 번에 읽는 경로. 결제 승인과 묶음 취소가 쓴다.
    INDEX idx_product_order_group (order_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_order_id BIGINT NOT NULL,
    product_item_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    CONSTRAINT pk_product_order_item PRIMARY KEY (id),
    CONSTRAINT uk_product_order_item_product UNIQUE (product_item_id),
    CONSTRAINT fk_product_order_item_order FOREIGN KEY (product_order_id) REFERENCES product_order (id),
    CONSTRAINT fk_product_order_item_product FOREIGN KEY (product_item_id) REFERENCES product_item (id),
    CONSTRAINT fk_product_order_item_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT chk_product_order_item_price CHECK (price >= 0),
    INDEX idx_product_order_item_order (product_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_shipping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_order_id BIGINT NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    carrier VARCHAR(50) NULL,
    tracking_number VARCHAR(100) NULL,
    shipped_at DATETIME NULL,
    CONSTRAINT pk_product_shipping PRIMARY KEY (id),
    CONSTRAINT uk_product_shipping_order UNIQUE (product_order_id),
    CONSTRAINT fk_product_shipping_order FOREIGN KEY (product_order_id) REFERENCES product_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE canceled_product_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_order_id BIGINT NOT NULL,
    order_code VARCHAR(30) NOT NULL,
    buyer_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    cancel_reason VARCHAR(255) NULL,
    original_created_at DATETIME NOT NULL,
    original_paid_at DATETIME NULL,
    canceled_at DATETIME NOT NULL,
    CONSTRAINT pk_canceled_product_order PRIMARY KEY (id),
    CONSTRAINT uk_canceled_product_order_original UNIQUE (original_order_id),
    CONSTRAINT fk_canceled_product_order_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_canceled_product_order_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_canceled_product_order_price CHECK (total_price >= 0),
    INDEX idx_canceled_product_order_buyer (buyer_id),
    INDEX idx_canceled_product_order_brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE canceled_product_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    canceled_product_order_id BIGINT NOT NULL,
    original_order_item_id BIGINT NOT NULL,
    product_item_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    CONSTRAINT pk_canceled_product_order_item PRIMARY KEY (id),
    CONSTRAINT fk_canceled_product_order_item_order FOREIGN KEY (canceled_product_order_id)
        REFERENCES canceled_product_order (id),
    CONSTRAINT fk_canceled_product_order_item_product FOREIGN KEY (product_item_id)
        REFERENCES product_item (id),
    CONSTRAINT fk_canceled_product_order_item_model FOREIGN KEY (product_model_id)
        REFERENCES product_model (id),
    CONSTRAINT chk_canceled_product_order_item_price CHECK (price >= 0),
    INDEX idx_canceled_product_order_item_order (canceled_product_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Prediction market ---------------------------------------------------------

CREATE TABLE prediction_market (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_market_id BIGINT NOT NULL,
    production_batch_id BIGINT NOT NULL,
    chain_batch_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_thumbnail_url VARCHAR(500) NULL,
    brand_id BIGINT NOT NULL,
    brand_wallet VARCHAR(42) NOT NULL,
    unit_price BIGINT NOT NULL,
    production_quantity INT NOT NULL,
    betting_ends_at DATETIME NOT NULL,
    sales_starts_at DATETIME NOT NULL,
    sales_ends_at DATETIME NOT NULL,
    status ENUM('OPEN', 'RESOLVING', 'RESOLVED', 'CANCELED') NOT NULL,
    sold_quantity INT NULL,
    winning_bucket_index INT NULL,
    result_hash CHAR(66) NULL,
    creation_tx_hash CHAR(66) NOT NULL,
    resolution_tx_hash CHAR(66) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    CONSTRAINT pk_prediction_market PRIMARY KEY (id),
    CONSTRAINT uk_prediction_market_chain UNIQUE (chain_market_id),
    CONSTRAINT uk_prediction_market_batch UNIQUE (production_batch_id),
    CONSTRAINT uk_prediction_market_chain_batch UNIQUE (chain_batch_id),
    CONSTRAINT fk_prediction_market_batch FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_prediction_market_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT fk_prediction_market_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_prediction_market_values CHECK (unit_price >= 0 AND production_quantity > 0),
    INDEX idx_prediction_market_status_end (status, sales_ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prediction_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market_id BIGINT NOT NULL,
    bucket_index INT NOT NULL,
    lower_bound INT NOT NULL,
    upper_bound INT NOT NULL,
    CONSTRAINT pk_prediction_bucket PRIMARY KEY (id),
    CONSTRAINT uk_prediction_bucket_market_index UNIQUE (market_id, bucket_index),
    CONSTRAINT fk_prediction_bucket_market FOREIGN KEY (market_id) REFERENCES prediction_market (id),
    CONSTRAINT chk_prediction_bucket_range CHECK (bucket_index >= 0 AND lower_bound >= 0 AND upper_bound >= lower_bound),
    INDEX idx_prediction_bucket_market (market_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prediction_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    bucket_index INT NOT NULL,
    stake_amount VARCHAR(78) NOT NULL,
    reason_code ENUM('DESIGN', 'PRICE', 'BRAND', 'SUSTAINABILITY', 'PRACTICALITY', 'TREND', 'OTHER') NOT NULL,
    reason_text VARCHAR(500) NULL,
    bet_tx_hash CHAR(66) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_prediction_response PRIMARY KEY (id),
    CONSTRAINT uk_prediction_response_market_user UNIQUE (market_id, user_id),
    CONSTRAINT uk_prediction_response_tx UNIQUE (bet_tx_hash),
    CONSTRAINT fk_prediction_response_market FOREIGN KEY (market_id) REFERENCES prediction_market (id),
    CONSTRAINT fk_prediction_response_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_prediction_response_market_bucket (market_id, bucket_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prediction_point_grant (
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    amount VARCHAR(78) NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_prediction_point_grant PRIMARY KEY (user_id),
    CONSTRAINT uk_prediction_point_grant_tx UNIQUE (tx_hash),
    CONSTRAINT fk_prediction_point_grant_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Marketplace / Trade -------------------------------------------------------

CREATE TABLE material_listing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_listing_id BIGINT NULL,
    material_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    total_quantity BIGINT NOT NULL,
    available_quantity BIGINT NOT NULL,
    price_per_kg BIGINT NOT NULL,
    material_lock_id BIGINT NOT NULL,
    status ENUM('ON_SALE', 'SOLD_OUT', 'CANCELED') NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_material_listing PRIMARY KEY (id),
    CONSTRAINT uk_material_listing_chain_listing_id UNIQUE (chain_listing_id),
    CONSTRAINT fk_material_listing_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_listing_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_material_listing_lock FOREIGN KEY (material_lock_id) REFERENCES material_lock (id),
    CONSTRAINT chk_material_listing_quantities CHECK (
        total_quantity > 0
        AND available_quantity >= 0
        AND available_quantity <= total_quantity
    ),
    CONSTRAINT chk_material_listing_price CHECK (price_per_kg >= 0),
    INDEX idx_material_listing_material_status (material_id, status),
    INDEX idx_material_listing_seller_status (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_trade_id BIGINT NULL,
    trade_code VARCHAR(30) NOT NULL,
    listing_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_wallet VARCHAR(42) NOT NULL,
    buyer_wallet VARCHAR(42) NOT NULL,
    quantity BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_amount BIGINT NOT NULL DEFAULT 0,
    status ENUM(
        'CREATED',
        'PAID',
        'SHIPPED',
        'RECEIVED',
        'COMPLETED',
        'CANCELED'
    ) NOT NULL DEFAULT 'CREATED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    shipped_at DATETIME NULL,
    received_at DATETIME NULL,
    -- Buyer 가 수령 확인과 함께 Wallet 으로 서명한 시각.
    -- received_at 은 수령 확인 시각이라 서명 사실을 대신하지 못한다.
    buyer_signed_at DATETIME NULL,
    completed_at DATETIME NULL,
    -- 취소는 상태만 남기면 "언제 왜 취소됐는가" 를 답할 수 없다. 분쟁이나 문의가
    -- 들어왔을 때 근거가 되는 값이라 사유와 시각을 함께 남긴다 (정책 7.3).
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    CONSTRAINT pk_material_trade PRIMARY KEY (id),
    CONSTRAINT uk_material_trade_code UNIQUE (trade_code),
    CONSTRAINT uk_material_trade_chain_trade_id UNIQUE (chain_trade_id),
    CONSTRAINT fk_material_trade_listing FOREIGN KEY (listing_id) REFERENCES material_listing (id),
    CONSTRAINT fk_material_trade_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_trade_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_material_trade_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT chk_material_trade_values CHECK (
        quantity > 0 AND total_price >= 0 AND payment_amount >= 0
    ),
    CONSTRAINT chk_material_trade_parties CHECK (seller_id <> buyer_id),
    INDEX idx_material_trade_listing (listing_id),
    INDEX idx_material_trade_material (material_id),
    INDEX idx_material_trade_seller_status (seller_id, status),
    INDEX idx_material_trade_buyer_status (buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_shipping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_id BIGINT NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    carrier VARCHAR(50) NULL,
    tracking_number VARCHAR(100) NULL,
    shipped_at DATETIME NULL,
    delivered_at DATETIME NULL,
    CONSTRAINT pk_trade_shipping PRIMARY KEY (id),
    CONSTRAINT uk_trade_shipping_trade UNIQUE (trade_id),
    CONSTRAINT fk_trade_shipping_trade FOREIGN KEY (trade_id) REFERENCES material_trade (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Blockchain ----------------------------------------------------------------

CREATE TABLE blockchain_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    reference_type ENUM('MATERIAL', 'TRADE', 'PROCESSING', 'PRODUCTION') NOT NULL,
    reference_id BIGINT NOT NULL,
    function_name VARCHAR(100) NOT NULL,
    from_wallet VARCHAR(42) NOT NULL,
    status ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
    block_number BIGINT NULL,
    log_index INT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    CONSTRAINT pk_blockchain_transaction PRIMARY KEY (id),
    CONSTRAINT uk_blockchain_transaction_event UNIQUE (chain_id, tx_hash, log_index),
    CONSTRAINT chk_blockchain_transaction_chain CHECK (chain_id > 0),
    CONSTRAINT chk_blockchain_transaction_block CHECK (block_number IS NULL OR block_number >= 0),
    CONSTRAINT chk_blockchain_transaction_log_index CHECK (log_index IS NULL OR log_index >= 0),
    INDEX idx_blockchain_transaction_reference (reference_type, reference_id),
    INDEX idx_blockchain_transaction_status (status),
    INDEX idx_blockchain_transaction_hash (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- User notification ---------------------------------------------------------

CREATE TABLE user_notification (
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

-- BRAND agent conversation ---------------------------------------------------
--
-- 대화형 에이전트의 세션·이력·대기 액션. phase/status/action_type 이 VARCHAR 인 이유와
-- JSON 성격 컬럼이 TEXT 인 이유는 db/migrations/2026-09-18-brand-agent-conversation.sql 참고.

CREATE TABLE agent_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    phase VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requirement_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_brand_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN brand_user_id ELSE NULL END),
    CONSTRAINT pk_agent_conversation PRIMARY KEY (id),
    CONSTRAINT uk_agent_conversation_active UNIQUE (active_brand_user_id),
    CONSTRAINT fk_agent_conversation_brand FOREIGN KEY (brand_user_id) REFERENCES users (id),
    INDEX idx_agent_conversation_brand (brand_user_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    sequence_no INT NOT NULL,
    content TEXT NULL,
    tool_name VARCHAR(100) NULL,
    tool_call_id VARCHAR(100) NULL,
    tool_payload_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_agent_message PRIMARY KEY (id),
    CONSTRAINT uk_agent_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT fk_agent_message_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id),
    INDEX idx_agent_message_conversation (conversation_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_pending_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json TEXT NULL,
    step_total INT NOT NULL DEFAULT 1,
    step_done INT NOT NULL DEFAULT 0,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_agent_pending_action PRIMARY KEY (id),
    CONSTRAINT fk_agent_pending_action_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id),
    CONSTRAINT fk_agent_pending_action_message FOREIGN KEY (message_id) REFERENCES agent_message (id),
    INDEX idx_agent_pending_action_conversation (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
