-- +migrate up
CREATE TABLE widget_draft (id INT PRIMARY KEY);

-- +migrate down
DROP TABLE widget_draft;
