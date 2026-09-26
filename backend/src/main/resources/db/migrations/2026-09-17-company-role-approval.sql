ALTER TABLE company_profile
    ADD COLUMN role_approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' AFTER logo_url,
    ADD COLUMN role_approved_wallet VARCHAR(42) NULL AFTER role_approval_status,
    ADD COLUMN role_approval_tx_hashes TEXT NULL AFTER role_approved_wallet,
    ADD COLUMN role_reviewed_at DATETIME NULL AFTER role_approval_tx_hashes,
    ADD COLUMN role_reviewed_by BIGINT NULL AFTER role_reviewed_at,
    ADD CONSTRAINT fk_company_profile_role_reviewer
        FOREIGN KEY (role_reviewed_by) REFERENCES users (id);
