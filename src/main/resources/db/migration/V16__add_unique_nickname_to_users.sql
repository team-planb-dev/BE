-- V16__add_unique_nickname_to_users.sql
-- 닉네임 유일성 제약

-- 이메일 찾기는 복구 답변만으로 계정을 특정할 수 없어 닉네임을 함께 받는다.
-- 닉네임이 계정을 가려내려면 유일해야 하므로 DB에서 보장한다.
-- 범위는 uk_users_username, existsByNickname과 같게 탈퇴(deleted='Y') 행도 포함한다.
ALTER TABLE users
    ADD CONSTRAINT uk_users_nickname
        UNIQUE (nickname);
