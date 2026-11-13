-- +migrate up
CREATE TABLE thing (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE thing;
