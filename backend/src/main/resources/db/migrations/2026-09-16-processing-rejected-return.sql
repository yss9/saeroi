-- 반려된 가공 작업의 입력 소재 반환 완료 상태를 추가한다.
-- 기존 ENUM 순서를 유지하고 마지막에만 추가해 이전 상태의 의미를 보존한다.
ALTER TABLE processing_job
    MODIFY COLUMN status ENUM(
        'CREATED','PAID','SHIPPED','RECEIVED','PROCESSING','RESULT_SUBMITTED',
        'RETURN_SHIPPED','CONFIRMED','REJECTED','EXPIRED','CANCELED'
    ) NOT NULL DEFAULT 'CREATED';
