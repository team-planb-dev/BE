-- ============================================================================
-- travel: add users_id (owner)
-- ============================================================================
ALTER TABLE travel
    ADD COLUMN users_id BIGINT NOT NULL AFTER travel_id;

ALTER TABLE travel
    ADD CONSTRAINT fk_travel_users
        FOREIGN KEY (users_id)
            REFERENCES users (id);