-- +migrate up
ALTER TABLE does_not_exist ADD COLUMN x INT;

-- +migrate down
DROP TABLE does_not_exist;
