-- V6__change_local_food_to_collection.sql

ALTER TABLE travel
DROP COLUMN local_food;

CREATE TABLE travel_local_food
(
    travel_id  BIGINT       NOT NULL,
    local_food VARCHAR(255) NOT NULL,

    CONSTRAINT fk_travel_local_food_travel
        FOREIGN KEY (travel_id)
            REFERENCES travel (travel_id)
            ON DELETE CASCADE
);