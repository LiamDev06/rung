-- +migrate up
ALTER TABLE widget ADD COLUMN name varchar(255);

-- +migrate down
ALTER TABLE widget DROP COLUMN name;
