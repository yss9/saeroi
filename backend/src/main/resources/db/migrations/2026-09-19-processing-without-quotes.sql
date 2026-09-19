-- 가공 흐름에서 견적 단계를 없앤다.
--
-- 무엇이 바뀌나
-- ─────────────────────────────────────────────────────────────────────
-- 예전 흐름: 요청 → 가공사가 견적 제출 → 브랜드가 견적 선택(= 작업 생성) → 결제 → 발송
-- 새 흐름:   요청(= 작업 생성) + 결제 → 가공사 수락/거절 → 발송
--
-- 브랜드가 검색 결과에서 가격과 소요일을 보고 바로 고르므로 받을 견적이 없다. 대신
-- 가공사가 미리 적어 둔 단가·하루 처리량으로 서버가 금액과 기간을 계산한다.
--
-- ⚠️ 적용 순서가 중요하다. 아래 DROP 은 되돌릴 수 없으므로, 진행 중인 가공 작업이 없는
--    시점에 적용한다. 진행 중인 작업이 있으면 먼저 끝내거나 취소·환불한 뒤에 돌린다.

-- 1) 가공사가 적는 것을 "수량 구간" 에서 "하루 처리량 + 단가" 로 바꾼다 ──────────────
--
-- 기존 행의 min/max 수량에는 단가 정보가 없어 옮겨 올 값이 없다. 그래서 가공사가 다시
-- 입력해야 하고, 그때까지 검색에 걸리지 않도록 단가를 0 이 아니라 기존 max_quantity 를
-- 하루 처리량으로만 물려준다(그 값이 "한 번에 이만큼까지 받는다" 였으므로 하루 처리량의
-- 근사치로는 가장 가깝다). 단가는 0 으로 두되, 0원 가공이 그대로 팔리지 않도록
-- 애플리케이션이 0 인 조합을 검색에서 제외한다.
ALTER TABLE processor_capability
    ADD COLUMN daily_capacity BIGINT NOT NULL DEFAULT 0 AFTER process_type,
    ADD COLUMN price_per_kg BIGINT NOT NULL DEFAULT 0 AFTER daily_capacity;

UPDATE processor_capability SET daily_capacity = GREATEST(max_quantity, 1);

ALTER TABLE processor_capability
    DROP CONSTRAINT chk_processor_capability_quantity;

ALTER TABLE processor_capability
    DROP COLUMN min_quantity,
    DROP COLUMN max_quantity;

ALTER TABLE processor_capability
    ALTER COLUMN daily_capacity DROP DEFAULT,
    ALTER COLUMN price_per_kg DROP DEFAULT;

ALTER TABLE processor_capability
    ADD CONSTRAINT chk_processor_capability_terms
        CHECK (daily_capacity > 0 AND price_per_kg >= 0);

-- 2) 소요일 표는 사라진다 — ⌈수량 ÷ 하루 처리량⌉ 으로 계산된다 ──────────────────────
DROP TABLE IF EXISTS processor_lead_time;

-- 3) 작업에 "수락 대기" 구간을 만든다 ─────────────────────────────────────────────
--
-- 결제와 발송 사이에 가공사의 답이 들어간다. 이 칸이 없으면 브랜드가 돈을 낸 직후에
-- 소재를 보내고, 가공사는 받고 나서야 못 하겠다고 말한다.
ALTER TABLE processing_job
    ADD COLUMN accept_expires_at DATETIME NULL AFTER payment_expires_at,
    ADD COLUMN accepted_at DATETIME NULL AFTER accept_expires_at,
    ADD COLUMN declined_at DATETIME NULL AFTER accepted_at,
    ADD COLUMN decline_reason VARCHAR(500) NULL AFTER declined_at,
    ADD COLUMN refunded_at DATETIME NULL AFTER decline_reason;

ALTER TABLE processing_job
    MODIFY COLUMN status ENUM(
        'CREATED',
        'PAID',
        'ACCEPTED',
        'SHIPPED',
        'RECEIVED',
        'PROCESSING',
        'RESULT_SUBMITTED',
        'RETURN_SHIPPED',
        'CONFIRMED',
        'REJECTED',
        'DECLINED',
        'EXPIRED',
        'CANCELED'
    ) NOT NULL DEFAULT 'CREATED';

ALTER TABLE processing_job
    ADD INDEX idx_processing_job_status_accept_expiry (status, accept_expires_at);

-- 4) 견적을 끊어 낸다 ────────────────────────────────────────────────────────────
--
-- processing_job.processing_quote_id 는 NOT NULL 이었고 FK·UNIQUE 가 걸려 있다.
-- 제약을 먼저 떼야 컬럼이 지워지고, 컬럼이 지워져야 테이블이 지워진다.
ALTER TABLE processing_job
    DROP FOREIGN KEY fk_processing_job_quote;

ALTER TABLE processing_job
    DROP INDEX uk_processing_job_quote;

ALTER TABLE processing_job
    DROP COLUMN processing_quote_id;

DROP TABLE IF EXISTS processing_quote;

-- 5) 요청 상태의 이름을 새 흐름에 맞춘다 ───────────────────────────────────────────
--
-- 자리는 같고 누가 다음 수를 두는지가 반대다 — 예전에는 브랜드가 견적을 골랐고
-- (QUOTING → SELECTED), 이제는 가공사가 요청을 받는다 (REQUESTED → ACCEPTED).
--
-- ENUM 에 새 값을 먼저 더한 뒤 데이터를 옮기고 옛 값을 뺀다. 순서를 바꾸면 기존 행이
-- 정의에 없는 값을 갖게 되어 ALTER 자체가 실패한다.
ALTER TABLE processing_request
    MODIFY COLUMN status ENUM(
        'QUOTING', 'SELECTED', 'REQUESTED', 'ACCEPTED', 'CANCELED', 'REJECTED', 'EXPIRED'
    ) NOT NULL DEFAULT 'QUOTING';

UPDATE processing_request SET status = 'REQUESTED' WHERE status = 'QUOTING';
UPDATE processing_request SET status = 'ACCEPTED' WHERE status = 'SELECTED';

ALTER TABLE processing_request
    MODIFY COLUMN status ENUM(
        'REQUESTED', 'ACCEPTED', 'CANCELED', 'REJECTED', 'EXPIRED'
    ) NOT NULL DEFAULT 'REQUESTED';
