-- +migrate Up
CREATE TABLE widget_draft (id INT PRIMARY KEY);

-- +migrate Down
DROP TABLE widget_draft;
