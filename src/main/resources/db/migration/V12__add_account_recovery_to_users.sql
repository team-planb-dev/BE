-- V12__add_account_recovery_to_users.sql


-- ============================================================================
-- users
-- ============================================================================
-- 이메일 찾기는 (질문, 답변 해시)로 조회하므로 두 컬럼에 인덱스를 건다.
ALTER TABLE users
    ADD COLUMN recovery_question VARCHAR(40) NOT NULL DEFAULT 'FIRST_PET',
    ADD COLUMN recovery_answer_hash VARCHAR(64) NOT NULL DEFAULT '';

CREATE INDEX idx_users_recovery
    ON users (recovery_question, recovery_answer_hash);
