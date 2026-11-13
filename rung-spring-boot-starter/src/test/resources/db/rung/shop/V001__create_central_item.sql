-- +migrate up
CREATE TABLE central_item (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE central_item;
