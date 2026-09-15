-- V17__add_health_disease_collection.sql
-- 관리 질환 복수 등록

-- 당뇨·고혈압·이상지질혈증은 함께 나타나는 경우가 많아 한 명이 여러 개를 관리한다.
-- 단일 컬럼으로는 하나만 남고 나머지는 버려지므로 별도 테이블로 옮긴다.
CREATE TABLE health_disease
(
    health_id    BIGINT       NOT NULL,
    disease_type VARCHAR(255) NOT NULL,

    CONSTRAINT pk_health_disease
        PRIMARY KEY (health_id, disease_type),

    CONSTRAINT fk_health_disease_health
        FOREIGN KEY (health_id) REFERENCES health (health_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 기존 행은 값이 하나씩이라 손실 없이 옮겨진다.
INSERT INTO health_disease (health_id, disease_type)
SELECT health_id, disease_type
FROM health
WHERE disease_type IS NOT NULL;
