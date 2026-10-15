ALTER TABLE travel
    ADD COLUMN edit_request_id VARCHAR(36) NULL,
    ADD COLUMN edit_preview_ready CHAR(1) NOT NULL DEFAULT 'N',
    ADD COLUMN confirmed_edit_request_id VARCHAR(36) NULL,
    ADD COLUMN edit_confirmed_at DATETIME(6) NULL;
