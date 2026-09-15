ALTER TABLE processing_output
    ADD COLUMN grade_reason VARCHAR(500) NULL AFTER grade;

UPDATE processing_output
SET grade_reason = '기존 데이터: 판정 사유 미입력'
WHERE grade_reason IS NULL OR TRIM(grade_reason) = '';

ALTER TABLE processing_output
    MODIFY COLUMN grade_reason VARCHAR(500) NOT NULL;
