-- +migrate Up
CREATE TABLE shop_item (id INT PRIMARY KEY);

-- +migrate Down
DROP TABLE shop_item;
