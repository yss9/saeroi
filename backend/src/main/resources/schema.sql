-- Upcycle MySQL 8 schema derived from ERD.md.
-- Run this script against the database selected by the connection.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    role ENUM('SUPPLIER', 'BRAND', 'PROCESSOR', 'CONSUMER', 'ADMIN') NOT NULL,
    status ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_wallet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_wallet PRIMARY KEY (id),
    CONSTRAINT uk_user_wallet_user UNIQUE (user_id),
    CONSTRAINT uk_user_wallet_address UNIQUE (wallet_address),
    CONSTRAINT fk_user_wallet_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_wallet_chain_id CHECK (chain_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_nonce (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    nonce CHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    CONSTRAINT pk_wallet_nonce PRIMARY KEY (id),
    CONSTRAINT fk_wallet_nonce_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wallet_nonce_chain_id CHECK (chain_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company_profile (
    user_id BIGINT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    business_number VARCHAR(50) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    introduction TEXT NULL,
    logo_url VARCHAR(500) NULL,
    CONSTRAINT pk_company_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_company_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
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
    token VARCHAR(500) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_refresh_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processor_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    region VARCHAR(100) NOT NULL,
    min_quantity BIGINT NOT NULL,
    max_quantity BIGINT NOT NULL,
    CONSTRAINT pk_processor_profile PRIMARY KEY (id),
    CONSTRAINT uk_processor_profile_user UNIQUE (user_id),
    CONSTRAINT fk_processor_profile_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_processor_profile_quantity CHECK (
        min_quantity >= 0 AND max_quantity >= min_quantity
    )
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
    CONSTRAINT pk_processor_capability PRIMARY KEY (id),
    CONSTRAINT uk_processor_capability UNIQUE (
        processor_profile_id,
        material_type,
        process_type
    ),
    CONSTRAINT fk_processor_capability_profile FOREIGN KEY (processor_profile_id)
        REFERENCES processor_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processor_lead_time (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processor_profile_id BIGINT NOT NULL,
    max_quantity BIGINT NOT NULL,
    lead_days INT NOT NULL,
    CONSTRAINT pk_processor_lead_time PRIMARY KEY (id),
    CONSTRAINT fk_processor_lead_time_profile FOREIGN KEY (processor_profile_id)
        REFERENCES processor_profile (id),
    CONSTRAINT chk_processor_lead_time_values CHECK (max_quantity > 0 AND lead_days > 0),
    INDEX idx_processor_lead_time_profile (processor_profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_code VARCHAR(20) NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    root_origin ENUM('VIRGIN', 'WASTE', 'RECOVERED') NOT NULL,
    generation_type ENUM('INITIAL', 'PROCESSED', 'PRODUCTION_SCRAP') NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    grade VARCHAR(50) NULL,
    total_quantity BIGINT NOT NULL,
    length_mm BIGINT NULL,
    width_mm BIGINT NULL,
    verification_state ENUM(
        'REGISTERED',
        'SOURCE_VERIFIED',
        'TRANSACTION_VERIFIED',
        'PROCESSING_VERIFIED',
        'PRODUCT_LINKED'
    ) NOT NULL DEFAULT 'REGISTERED',
    registrant_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'DEPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_material PRIMARY KEY (id),
    CONSTRAINT uk_material_code UNIQUE (material_code),
    CONSTRAINT fk_material_registrant FOREIGN KEY (registrant_id) REFERENCES users (id),
    CONSTRAINT chk_material_quantity CHECK (total_quantity >= 0),
    CONSTRAINT chk_material_dimensions CHECK (
        (length_mm IS NULL OR length_mm > 0)
        AND (width_mm IS NULL OR width_mm > 0)
    ),
    INDEX idx_material_type_origin (material_type, root_origin),
    INDEX idx_material_verification_state (verification_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_material_balance PRIMARY KEY (id),
    CONSTRAINT uk_material_balance_owner UNIQUE (material_id, owner_id),
    CONSTRAINT fk_material_balance_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_balance_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_material_balance_quantity CHECK (quantity >= 0),
    INDEX idx_material_balance_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_lock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    purpose ENUM('MARKETPLACE', 'PROCESSING', 'PRODUCTION') NOT NULL,
    reference_type ENUM('LISTING', 'TRADE', 'PROCESSING_JOB', 'PRODUCTION_BATCH') NOT NULL,
    reference_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'RELEASED', 'CONSUMED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at DATETIME NULL,
    CONSTRAINT pk_material_lock PRIMARY KEY (id),
    CONSTRAINT fk_material_lock_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_lock_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_material_lock_amount CHECK (
        amount >= 0 AND (status <> 'ACTIVE' OR amount > 0)
    ),
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

CREATE TABLE evidence_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_type ENUM(
        'MATERIAL',
        'PROCESSING_JOB',
        'PRODUCTION_BATCH',
        'PRODUCTION_INPUT',
        'TRADE_DISPUTE'
    ) NOT NULL,
    reference_id BIGINT NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_hash CHAR(66) NOT NULL,
    evidence_type ENUM(
        'BEFORE_PHOTO',
        'AFTER_PHOTO',
        'WEIGHT_SLIP',
        'INVOICE',
        'PROCESS_PHOTO',
        'ETC'
    ) NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_evidence_file PRIMARY KEY (id),
    INDEX idx_evidence_file_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_code VARCHAR(30) NOT NULL,
    brand_id BIGINT NOT NULL,
    status ENUM('QUOTING', 'SELECTED', 'CANCELED', 'EXPIRED') NOT NULL DEFAULT 'QUOTING',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    CONSTRAINT pk_processing_request PRIMARY KEY (id),
    CONSTRAINT uk_processing_request_code UNIQUE (request_code),
    CONSTRAINT fk_processing_request_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    INDEX idx_processing_request_brand_status (brand_id, status),
    INDEX idx_processing_request_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_request_input (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_request_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_processing_request_input PRIMARY KEY (id),
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
    CONSTRAINT pk_processing_request_type PRIMARY KEY (id),
    CONSTRAINT uk_processing_request_type UNIQUE (processing_request_id, process_type),
    CONSTRAINT fk_processing_request_type_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id)
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
    fee BIGINT NOT NULL,
    loss_quantity BIGINT NOT NULL DEFAULT 0,
    evidence_hash CHAR(66) NULL,
    status ENUM(
        'PAID',
        'SHIPPED',
        'RECEIVED',
        'PROCESSING',
        'RESULT_SUBMITTED',
        'CONFIRMED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PAID',
    rejection_reason TEXT NULL,
    processor_submitted_at DATETIME NULL,
    brand_approved_at DATETIME NULL,
    brand_rejected_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at DATETIME NULL,
    received_at DATETIME NULL,
    completed_at DATETIME NULL,
    CONSTRAINT pk_processing_job PRIMARY KEY (id),
    CONSTRAINT uk_processing_job_code UNIQUE (job_code),
    CONSTRAINT uk_processing_job_quote UNIQUE (processing_quote_id),
    CONSTRAINT fk_processing_job_request FOREIGN KEY (processing_request_id)
        REFERENCES processing_request (id),
    CONSTRAINT fk_processing_job_quote FOREIGN KEY (processing_quote_id) REFERENCES processing_quote (id),
    CONSTRAINT fk_processing_job_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT fk_processing_job_processor FOREIGN KEY (processor_id) REFERENCES users (id),
    CONSTRAINT chk_processing_job_amounts CHECK (fee >= 0 AND loss_quantity >= 0),
    INDEX idx_processing_job_processor_status (processor_id, status),
    INDEX idx_processing_job_brand_status (brand_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_input (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_job_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    material_lock_id BIGINT NOT NULL,
    CONSTRAINT pk_processing_input PRIMARY KEY (id),
    CONSTRAINT uk_processing_input_job UNIQUE (processing_job_id),
    CONSTRAINT fk_processing_input_job FOREIGN KEY (processing_job_id) REFERENCES processing_job (id),
    CONSTRAINT fk_processing_input_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_processing_input_lock FOREIGN KEY (material_lock_id) REFERENCES material_lock (id),
    CONSTRAINT chk_processing_input_quantity CHECK (quantity > 0),
    INDEX idx_processing_input_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processing_output (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processing_job_id BIGINT NOT NULL,
    output_material_id BIGINT NULL,
    material_type VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT pk_processing_output PRIMARY KEY (id),
    CONSTRAINT fk_processing_output_job FOREIGN KEY (processing_job_id) REFERENCES processing_job (id),
    CONSTRAINT fk_processing_output_material FOREIGN KEY (output_material_id) REFERENCES material (id),
    CONSTRAINT chk_processing_output_quantity CHECK (quantity > 0),
    INDEX idx_processing_output_job (processing_job_id),
    INDEX idx_processing_output_material (output_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    brand_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    description TEXT NULL,
    thumbnail_url VARCHAR(500) NULL,
    status ENUM('DRAFT', 'ON_SALE', 'SOLD_OUT', 'DISCONTINUED') NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_model PRIMARY KEY (id),
    CONSTRAINT fk_product_model_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_product_model_price CHECK (price >= 0),
    INDEX idx_product_model_brand_status (brand_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_code VARCHAR(20) NOT NULL,
    batch_name VARCHAR(200) NOT NULL,
    product_model_id BIGINT NOT NULL,
    produced_quantity INT NOT NULL,
    producer_id BIGINT NOT NULL,
    producer_wallet VARCHAR(42) NOT NULL,
    status ENUM('DRAFT', 'SIGNING', 'CONFIRMED', 'FAILED', 'CANCELED') NOT NULL DEFAULT 'DRAFT',
    total_used_quantity BIGINT NOT NULL DEFAULT 0,
    virgin_used_quantity BIGINT NOT NULL DEFAULT 0,
    upcycled_used_quantity BIGINT NOT NULL DEFAULT 0,
    upcycle_ratio DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    evidence_hash CHAR(66) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    failed_at DATETIME NULL,
    failure_reason VARCHAR(255) NULL,
    CONSTRAINT pk_production_batch PRIMARY KEY (id),
    CONSTRAINT uk_production_batch_code UNIQUE (batch_code),
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

CREATE TABLE product_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_batch_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    serial_number INT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    qr_token CHAR(32) NOT NULL,
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

CREATE TABLE product_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(30) NOT NULL,
    buyer_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    status ENUM('CREATED', 'PAID', 'SHIPPED', 'DELIVERED') NOT NULL DEFAULT 'CREATED',
    payment_method ENUM('POINT', 'MOCK') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_deadline DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    shipped_at DATETIME NULL,
    delivered_at DATETIME NULL,
    CONSTRAINT pk_product_order PRIMARY KEY (id),
    CONSTRAINT uk_product_order_code UNIQUE (order_code),
    CONSTRAINT fk_product_order_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_product_order_brand FOREIGN KEY (brand_id) REFERENCES users (id),
    CONSTRAINT chk_product_order_total_price CHECK (total_price >= 0),
    INDEX idx_product_order_buyer_status (buyer_id, status),
    INDEX idx_product_order_brand_status (brand_id, status)
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
    payment_method ENUM('POINT', 'MOCK') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    cancel_reason VARCHAR(255) NULL,
    original_created_at DATETIME NOT NULL,
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

CREATE TABLE material_listing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    total_quantity BIGINT NOT NULL,
    available_quantity BIGINT NOT NULL,
    price_per_kg BIGINT NOT NULL,
    material_lock_id BIGINT NOT NULL,
    status ENUM('ON_SALE', 'SOLD_OUT', 'CANCELED') NOT NULL DEFAULT 'ON_SALE',
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    CONSTRAINT pk_material_listing PRIMARY KEY (id),
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
    trade_code VARCHAR(30) NOT NULL,
    listing_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_wallet VARCHAR(42) NOT NULL,
    buyer_wallet VARCHAR(42) NOT NULL,
    quantity BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    status ENUM(
        'CREATED',
        'PAID',
        'SHIPPED',
        'RECEIVED',
        'COMPLETED',
        'CANCELED',
        'DISPUTED'
    ) NOT NULL DEFAULT 'CREATED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    shipped_at DATETIME NULL,
    received_at DATETIME NULL,
    completed_at DATETIME NULL,
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    CONSTRAINT pk_material_trade PRIMARY KEY (id),
    CONSTRAINT uk_material_trade_code UNIQUE (trade_code),
    CONSTRAINT fk_material_trade_listing FOREIGN KEY (listing_id) REFERENCES material_listing (id),
    CONSTRAINT fk_material_trade_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_trade_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_material_trade_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT chk_material_trade_values CHECK (quantity > 0 AND total_price >= 0),
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

CREATE TABLE trade_dispute (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_id BIGINT NOT NULL,
    raised_by BIGINT NOT NULL,
    reason_type ENUM('NOT_DELIVERED', 'QUANTITY_MISMATCH', 'CONDITION_MISMATCH') NOT NULL,
    description TEXT NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    resolution ENUM('BUYER_WIN', 'SELLER_WIN') NULL,
    admin_id BIGINT NULL,
    resolved_at DATETIME NULL,
    CONSTRAINT pk_trade_dispute PRIMARY KEY (id),
    CONSTRAINT fk_trade_dispute_trade FOREIGN KEY (trade_id) REFERENCES material_trade (id),
    CONSTRAINT fk_trade_dispute_raiser FOREIGN KEY (raised_by) REFERENCES users (id),
    CONSTRAINT fk_trade_dispute_admin FOREIGN KEY (admin_id) REFERENCES users (id),
    INDEX idx_trade_dispute_trade (trade_id),
    INDEX idx_trade_dispute_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE blockchain_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tx_hash CHAR(66) NOT NULL,
    reference_type ENUM('MATERIAL', 'TRADE', 'PROCESSING', 'PRODUCTION', 'DISPUTE') NOT NULL,
    reference_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    function_name VARCHAR(100) NOT NULL,
    from_wallet VARCHAR(42) NOT NULL,
    status ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
    block_number BIGINT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    CONSTRAINT pk_blockchain_transaction PRIMARY KEY (id),
    CONSTRAINT chk_blockchain_transaction_chain CHECK (chain_id > 0),
    CONSTRAINT chk_blockchain_transaction_block CHECK (block_number IS NULL OR block_number >= 0),
    INDEX idx_blockchain_transaction_reference (reference_type, reference_id),
    INDEX idx_blockchain_transaction_hash (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    locked BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_point_account PRIMARY KEY (id),
    CONSTRAINT uk_point_account_user UNIQUE (user_id),
    CONSTRAINT fk_point_account_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_point_account_amounts CHECK (
        balance >= 0 AND locked >= 0 AND locked <= balance
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    type ENUM(
        'CHARGE',
        'TRADE_PAY',
        'TRADE_SETTLE',
        'PROCESSING_PAY',
        'PROCESSING_SETTLE',
        'PRODUCT_PAY',
        'REFUND'
    ) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_point_transaction PRIMARY KEY (id),
    CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_point_transaction_amount CHECK (amount <> 0),
    INDEX idx_point_transaction_user_created (user_id, created_at),
    INDEX idx_point_transaction_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
