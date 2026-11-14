-- +migrate up
CREATE TABLE badge (id INT PRIMARY KEY, player_id INT NOT NULL REFERENCES player (id));

-- +migrate down
DROP TABLE badge;
