-- V3__add_health_domain.sql
-- add health domain schema


-- ============================================================================
-- health
-- ============================================================================
CREATE TABLE health
(
    health_id           BIGINT       NOT NULL AUTO_INCREMENT,
    users_id            BIGINT       NOT NULL,
    traveler_name       VARCHAR(255) NOT NULL,

    sensitive_agree     VARCHAR(1)   NOT NULL DEFAULT 'N',
    has_medication      VARCHAR(1)   NOT NULL DEFAULT 'N',

    disease_type        VARCHAR(255) NULL,
    walk_type           VARCHAR(255) NULL,

    meal_applied        VARCHAR(1)   NOT NULL DEFAULT 'N',

    breakfast_applied   VARCHAR(1)   NOT NULL DEFAULT 'N',
    breakfast_time      TIME         NULL,

    lunch_applied       VARCHAR(1)   NOT NULL DEFAULT 'N',
    lunch_time          TIME         NULL,

    dinner_applied      VARCHAR(1)   NOT NULL DEFAULT 'N',
    dinner_time         TIME         NULL,

    CONSTRAINT pk_health
        PRIMARY KEY (health_id),

    CONSTRAINT fk_health_users
        FOREIGN KEY (users_id)
            REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- food_info
-- ============================================================================
CREATE TABLE food_info
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    health_id   BIGINT       NOT NULL,
    food_name   VARCHAR(255) NULL,
    food_type   VARCHAR(255) NULL,

    CONSTRAINT pk_food_info
        PRIMARY KEY (id),

    CONSTRAINT fk_food_info_health
        FOREIGN KEY (health_id)
            REFERENCES health (health_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- medication_info
-- ============================================================================
CREATE TABLE medication_info
(
    medication_info_id BIGINT       NOT NULL AUTO_INCREMENT,
    health_id          BIGINT       NOT NULL,

    drug_name          VARCHAR(255) NULL,
    medication_basis   VARCHAR(255) NULL,
    medication_time    TIME         NULL,

    CONSTRAINT pk_medication_info
        PRIMARY KEY (medication_info_id),

    CONSTRAINT fk_medication_info_health
        FOREIGN KEY (health_id)
            REFERENCES health (health_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- medication_meal_rule
-- ============================================================================
CREATE TABLE medication_meal_rule
(
    medication_info_id BIGINT       NOT NULL,
    related_meal       VARCHAR(255) NULL,
    meal_timing        VARCHAR(255) NULL,
    interval_minutes   INT          NULL,

    CONSTRAINT fk_medication_meal_rule_medication
        FOREIGN KEY (medication_info_id)
            REFERENCES medication_info (medication_info_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;