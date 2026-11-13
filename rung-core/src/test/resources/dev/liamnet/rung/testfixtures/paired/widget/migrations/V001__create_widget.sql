-- +migrate up
CREATE TABLE widget (id INT PRIMARY KEY);

CREATE INDEX idx_widget_id ON widget (id);

-- +migrate down
DROP INDEX idx_widget_id;

DROP TABLE widget;
