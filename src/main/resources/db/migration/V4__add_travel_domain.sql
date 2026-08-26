-- V4__add_travel_domain.sql
-- add travel domain schema


-- ============================================================================
-- travel
-- ============================================================================
CREATE TABLE travel
(
    travel_id           BIGINT       NOT NULL AUTO_INCREMENT,
    travel_name         VARCHAR(255) NULL,
    location_do         VARCHAR(255) NULL,
    location_sigungu    VARCHAR(255) NULL,

    start_date          DATE         NULL,
    end_date            DATE         NULL,

    date_type           VARCHAR(255) NULL,
    transportation      VARCHAR(255) NULL,
    travel_style        VARCHAR(255) NULL,
    travel_theme        VARCHAR(255) NULL,

    local_food          VARCHAR(255) NULL,
    decided_location    VARCHAR(255) NULL,

    CONSTRAINT pk_travel
        PRIMARY KEY (travel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- travel_recommend_food
-- ============================================================================
CREATE TABLE travel_recommend_food
(
    travel_id   BIGINT       NOT NULL,
    food_name   VARCHAR(255) NULL,

    CONSTRAINT fk_travel_recommend_food_travel
        FOREIGN KEY (travel_id)
            REFERENCES travel (travel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- planned_place
-- ============================================================================
CREATE TABLE planned_place
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    travel_id       BIGINT       NOT NULL,

    location_name   VARCHAR(255) NOT NULL,
    location        VARCHAR(255) NULL,

    CONSTRAINT pk_planned_place
        PRIMARY KEY (id),

    CONSTRAINT fk_planned_place_travel
        FOREIGN KEY (travel_id)
            REFERENCES travel (travel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- plan
-- ============================================================================
CREATE TABLE plan
(
    plan_id     BIGINT       NOT NULL AUTO_INCREMENT,
    travel_id   BIGINT       NOT NULL,
    plan_name   VARCHAR(255) NULL,

    CONSTRAINT pk_plan
        PRIMARY KEY (plan_id),

    CONSTRAINT uk_plan_travel
        UNIQUE (travel_id),

    CONSTRAINT fk_plan_travel
        FOREIGN KEY (travel_id)
            REFERENCES travel (travel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- plan_day
-- ============================================================================
CREATE TABLE plan_day
(
    plan_day_id    BIGINT NOT NULL AUTO_INCREMENT,
    plan_id        BIGINT NOT NULL,

    day_number     INT    NULL,
    plan_date      DATE   NULL,

    CONSTRAINT pk_plan_day
        PRIMARY KEY (plan_day_id),

    CONSTRAINT fk_plan_day_plan
        FOREIGN KEY (plan_id)
            REFERENCES plan (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- plan_schedule
-- ============================================================================
CREATE TABLE plan_schedule
(
    plan_schedule_id                BIGINT       NOT NULL AUTO_INCREMENT,
    plan_day_id                     BIGINT       NOT NULL,

    schedule_type                   VARCHAR(255) NOT NULL,
    course_type                     VARCHAR(255) NOT NULL,

    start_time                      TIME         NULL,
    end_time                        TIME         NULL,

    location_name                   VARCHAR(255) NULL,
    image_url                       VARCHAR(255) NULL,
    recommended_menu                VARCHAR(255) NULL,
    location                        VARCHAR(255) NULL,

    stay_minutes                    INT          NULL,
    travel_minutes                  INT          NULL,

    medication_interval_minutes     INT          NULL,
    medication_description          VARCHAR(255) NULL,

    CONSTRAINT pk_plan_schedule
        PRIMARY KEY (plan_schedule_id),

    CONSTRAINT fk_plan_schedule_plan_day
        FOREIGN KEY (plan_day_id)
            REFERENCES plan_day (plan_day_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
-- plan_schedule_tag
-- ============================================================================
CREATE TABLE plan_schedule_tag
(
    plan_schedule_id    BIGINT       NOT NULL,
    tag                 VARCHAR(255) NULL,

    CONSTRAINT fk_plan_schedule_tag_plan_schedule
        FOREIGN KEY (plan_schedule_id)
            REFERENCES plan_schedule (plan_schedule_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;