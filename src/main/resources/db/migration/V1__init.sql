-- V1__init.sql
-- schema initialization


-- ============================================================================
-- users
-- ============================================================================
CREATE TABLE users
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(255) NOT NULL,
    nickname   VARCHAR(255) NOT NULL,
    deleted    VARCHAR(1)   NOT NULL DEFAULT 'N',
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    deleted_at DATETIME(6)  NULL,

    CONSTRAINT pk_users
        PRIMARY KEY (id),

    CONSTRAINT uk_users_username
        UNIQUE (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;



-- ============================================================================
-- chat_room
-- ============================================================================
CREATE TABLE chat_room
(
    chat_room_id  BIGINT       NOT NULL AUTO_INCREMENT,
    chat_room_name VARCHAR(255) NULL,
    deleted        VARCHAR(1)   NOT NULL DEFAULT 'N',
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    deleted_at     DATETIME(6)  NULL,

    CONSTRAINT pk_chat_room
        PRIMARY KEY (chat_room_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;



-- ============================================================================
-- chat_room_member
-- ============================================================================
CREATE TABLE chat_room_member
(
    id           BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,

    CONSTRAINT pk_chat_room_member
        PRIMARY KEY (id),

    CONSTRAINT uk_chat_room_member
        UNIQUE (chat_room_id, user_id),

    CONSTRAINT fk_chat_room_member_chat_room
        FOREIGN KEY (chat_room_id)
            REFERENCES chat_room (chat_room_id),

    CONSTRAINT fk_chat_room_member_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;



-- ============================================================================
-- chat message
-- ============================================================================
CREATE TABLE chat_message
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT      NOT NULL,
    sender_id    BIGINT      NOT NULL,
    message      TEXT        NOT NULL,
    send_at      DATETIME(6) NULL,
    deleted      VARCHAR(1)  NOT NULL DEFAULT 'N',
    created_at   DATETIME(6) NULL,
    updated_at   DATETIME(6) NULL,
    deleted_at   DATETIME(6) NULL,

    CONSTRAINT pk_chat_message
        PRIMARY KEY (id),

    CONSTRAINT fk_chat_message_chat_room
        FOREIGN KEY (chat_room_id)
            REFERENCES chat_room (chat_room_id),

    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_id)
            REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;