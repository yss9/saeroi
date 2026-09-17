ALTER TABLE canceled_product_order
    ADD COLUMN original_paid_at DATETIME NULL AFTER original_created_at;

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
    PRIMARY KEY (id), UNIQUE (chain_market_id), UNIQUE (production_batch_id), UNIQUE (chain_batch_id),
    FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    FOREIGN KEY (brand_id) REFERENCES users (id),
    INDEX idx_prediction_market_status_end (status, sales_ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prediction_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    market_id BIGINT NOT NULL,
    bucket_index INT NOT NULL,
    lower_bound INT NOT NULL,
    upper_bound INT NOT NULL,
    PRIMARY KEY (id), UNIQUE (market_id, bucket_index),
    FOREIGN KEY (market_id) REFERENCES prediction_market (id),
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
    PRIMARY KEY (id), UNIQUE (market_id, user_id), UNIQUE (bet_tx_hash),
    FOREIGN KEY (market_id) REFERENCES prediction_market (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_prediction_response_market_bucket (market_id, bucket_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prediction_point_grant (
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    amount VARCHAR(78) NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id), UNIQUE (tx_hash),
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
