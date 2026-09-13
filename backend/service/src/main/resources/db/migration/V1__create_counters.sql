-- `ordinal` exists only to answer "oldest first": ids are random, so nothing else about a row says
-- when it was made. An identity rather than a timestamp, because two counters made in the same
-- instant still need an order.
CREATE TABLE counters (
    id      TEXT   PRIMARY KEY,
    count   BIGINT NOT NULL,
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY
);
