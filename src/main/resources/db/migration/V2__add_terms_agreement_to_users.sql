-- V2__add_terms_agreement_to_users.sql


-- ============================================================================
-- users
-- ============================================================================
ALTER TABLE users
    ADD COLUMN age_requirement_agreed VARCHAR(1) NOT NULL DEFAULT 'N',
    ADD COLUMN service_terms_agreed VARCHAR(1) NOT NULL DEFAULT 'N',
    ADD COLUMN privacy_collection_agreed VARCHAR(1) NOT NULL DEFAULT 'N';