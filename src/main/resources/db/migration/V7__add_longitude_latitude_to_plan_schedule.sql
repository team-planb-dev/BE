-- V7__add_longitude_latitude_to_plan_schedule.sql
-- add longitude/latitude columns to plan_schedule for CAFE_REST / findPlaceWithRoute로 확인된 ATTRACTION 좌표

ALTER TABLE plan_schedule
    ADD COLUMN longitude VARCHAR(255) NULL AFTER location,
    ADD COLUMN latitude  VARCHAR(255) NULL AFTER longitude;