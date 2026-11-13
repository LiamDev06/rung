-- +migrate Up
ALTER TABLE does_not_exist ADD COLUMN x INT;

-- +migrate Down
DROP TABLE does_not_exist;
