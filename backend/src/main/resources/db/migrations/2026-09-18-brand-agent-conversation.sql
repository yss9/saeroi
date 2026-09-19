-- BRAND 대화형 에이전트 — 대화 세션, 메시지, 사용자 확인 대기 액션.
--
-- 세 테이블로 나눈 이유
--   agent_conversation   여정 하나. 사용자가 끝낼 때까지 살아 있고 phase 가 진행 상태다.
--   agent_message        LLM 에 다시 넣을 대화 이력. tool 호출과 그 결과도 여기 남는다.
--   agent_pending_action 서버가 대신 할 수 없는 일(지갑 서명·결제)의 대기 지점.
--
-- phase/status/action_type 을 DB ENUM 이 아니라 VARCHAR 로 둔다. material_type 과 같은
-- 이유다(schema.sql:137 참고) — 여정 단계는 앞으로 늘어날 값이라 enum 을 고칠 때마다
-- 스키마 마이그레이션을 얹고 싶지 않다.
--
-- JSON 성격의 컬럼은 TEXT 다. 이 스키마에 JSON 타입 컬럼이 아직 하나도 없고, 테스트가
-- H2(MODE=MySQL)로 도는데 H2 의 JSON 타입은 Hibernate 매핑이 MySQL 과 달라서다.

CREATE TABLE IF NOT EXISTS agent_conversation (
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

-- sequence 는 대화 안에서의 순번이다. created_at 으로 정렬하지 않는 이유는 한 턴에
-- assistant/tool 메시지가 같은 초에 여러 개 쌓여 순서가 흔들리기 때문이다.
CREATE TABLE IF NOT EXISTS agent_message (
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

-- 에이전트는 상태를 바꾸는 일을 직접 하지 않는다. 여기에 제안을 남기고, 사용자가
-- 화면에서 서명하거나 결제한 결과를 result_json 으로 받아 그때 실제 도메인 서비스를
-- 부른다. expires_at 은 오래된 제안이 되살아나 엉뚱한 시점에 실행되는 것을 막는다.
CREATE TABLE IF NOT EXISTS agent_pending_action (
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
