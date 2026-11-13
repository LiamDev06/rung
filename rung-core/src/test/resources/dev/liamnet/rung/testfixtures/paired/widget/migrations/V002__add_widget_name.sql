-- +migrate Up
ALTER TABLE widget ADD COLUMN name varchar(255);

-- +migrate Down
ALTER TABLE widget DROP COLUMN name;
