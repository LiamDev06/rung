-- +migrate up
CREATE TABLE shop_item (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE shop_item;
