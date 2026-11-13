-- +migrate Up
CREATE TABLE thing (id INT PRIMARY KEY);

-- +migrate Down
DROP TABLE thing;
