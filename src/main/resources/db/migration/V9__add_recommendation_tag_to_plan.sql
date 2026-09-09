-- V9__add_recommendation_tag_to_plan.sql

CREATE TABLE plan_recommendation_tag
(
    plan_id BIGINT       NOT NULL,
    tag     VARCHAR(255) NULL,

    CONSTRAINT fk_plan_recommendation_tag_plan
        FOREIGN KEY (plan_id)
            REFERENCES plan (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;