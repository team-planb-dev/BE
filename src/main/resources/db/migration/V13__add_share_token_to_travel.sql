-- ============================================================================
-- travel: add share_token (read-only share link)
-- ============================================================================
-- 공유하지 않은 여행은 NULL이며, MySQL의 UNIQUE는 NULL 중복을 허용하므로 그대로 둔다.
ALTER TABLE travel
    ADD COLUMN share_token VARCHAR(36) NULL;

ALTER TABLE travel
    ADD CONSTRAINT uk_travel_share_token UNIQUE (share_token);
