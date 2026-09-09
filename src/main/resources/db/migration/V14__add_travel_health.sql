-- ============================================================================
-- travel_health: 여행별 참여 구성원 연결
-- ============================================================================
-- Health는 사용자 계정에 남아 있고 여행마다 선택만 달라지므로 별도 관계로 저장한다.
CREATE TABLE travel_health
(
    travel_health_id BIGINT NOT NULL AUTO_INCREMENT,
    travel_id        BIGINT NOT NULL,
    health_id        BIGINT NOT NULL,
    PRIMARY KEY (travel_health_id),
    CONSTRAINT uk_travel_health UNIQUE (travel_id, health_id),
    CONSTRAINT fk_travel_health_travel
        FOREIGN KEY (travel_id) REFERENCES travel (travel_id),
    CONSTRAINT fk_travel_health_health
        FOREIGN KEY (health_id) REFERENCES health (health_id)
);
