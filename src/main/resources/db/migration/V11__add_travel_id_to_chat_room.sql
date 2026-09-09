-- V11__add_travel_id_to_chat_room.sql

ALTER TABLE chat_room
    ADD COLUMN travel_id BIGINT NULL;

ALTER TABLE chat_room
    ADD CONSTRAINT uk_chat_room_travel_id
        UNIQUE (travel_id);

ALTER TABLE chat_room
    ADD CONSTRAINT fk_chat_room_travel
        FOREIGN KEY (travel_id)
            REFERENCES travel (travel_id);
