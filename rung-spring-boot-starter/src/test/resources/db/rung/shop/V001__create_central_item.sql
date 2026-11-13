-- +migrate Up
CREATE TABLE central_item (id INT PRIMARY KEY);

-- +migrate Down
DROP TABLE central_item;
