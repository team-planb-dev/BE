-- V5__update_plan_schedule_and_add_restaurant_detail.sql
-- update travel plan schema and add restaurant detail


-- ============================================================================
-- plan_schedule
-- ============================================================================
ALTER TABLE plan_schedule
DROP COLUMN recommended_menu,
    ADD COLUMN thumb_nail_image_url VARCHAR(255) NULL
        AFTER image_url;


-- ============================================================================
-- restaurant_detail
-- ============================================================================
CREATE TABLE restaurant_detail
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    plan_schedule_id    BIGINT       NOT NULL,

    menu_name           VARCHAR(255) NULL,

    carbohydrate        DOUBLE       NULL,
    sodium              DOUBLE       NULL,
    fat                 DOUBLE       NULL,

    open_time           VARCHAR(255) NULL,
    address             VARCHAR(255) NULL,

    longitude           VARCHAR(255) NULL,
    latitude            VARCHAR(255) NULL,

    image_url           VARCHAR(255) NULL,

    CONSTRAINT pk_restaurant_detail
        PRIMARY KEY (id),

    CONSTRAINT uk_restaurant_detail_plan_schedule
        UNIQUE (plan_schedule_id),

    CONSTRAINT fk_restaurant_detail_plan_schedule
        FOREIGN KEY (plan_schedule_id)
            REFERENCES plan_schedule (plan_schedule_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;