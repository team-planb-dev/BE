-- V18__drop_health_disease_type_column.sql
-- 단일 질환 컬럼 제거

-- V17이 값을 health_disease로 모두 옮겼으므로 이 컬럼은 더 이상 읽히지 않는다.
-- 드롭은 되돌릴 수 없어 V17과 분리했다. 배포 동작을 확인한 뒤 적용한다.
ALTER TABLE health
    DROP COLUMN disease_type;
