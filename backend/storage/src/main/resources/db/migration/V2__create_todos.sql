-- Each person's own todos. `owner` is who they belong to, and every query names it: there is no
-- way to reach a todo except as the person it belongs to.
--
-- `ordinal` for "oldest first", for the same reason `counters` has one.
CREATE TABLE todos (
    id      TEXT    PRIMARY KEY,
    owner   TEXT    NOT NULL,
    title   TEXT    NOT NULL,
    done    BOOLEAN NOT NULL,
    ordinal BIGINT  GENERATED ALWAYS AS IDENTITY
);

-- What listing asks for: one person's todos, in order.
CREATE INDEX todos_by_owner ON todos (owner, ordinal);
