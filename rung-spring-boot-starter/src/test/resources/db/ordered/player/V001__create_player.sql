-- +migrate up
CREATE TABLE player (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE player;
