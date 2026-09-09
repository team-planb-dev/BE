-- V10__add_ai_bot_user.sql

INSERT INTO users (username, password, role, nickname, deleted,
                    age_requirement_agreed, service_terms_agreed, privacy_collection_agreed,
                    created_at, updated_at)
VALUES ('ai-assistant@planb.system',
        '$2b$10$b6tZyLF1e24/dYc09gdbm.jAReXZZdY5y8jXqtojNQowHPGPCHhL2',
        'USER',
        'AI 비서',
        'N',
        'Y', 'Y', 'Y',
        NOW(6), NOW(6));
