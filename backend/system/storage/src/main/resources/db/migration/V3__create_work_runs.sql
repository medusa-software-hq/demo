-- Work people set going, and what became of it. Written by the worker as the work goes: each step it
-- finishes moves `steps_done`, and the last fills in `result`.
--
-- `owner`, as for todos: a run is somebody's, and read back only as theirs.
CREATE TABLE work_runs (
    id          TEXT    PRIMARY KEY,
    owner       TEXT    NOT NULL,
    steps_total INTEGER NOT NULL,
    steps_done  INTEGER NOT NULL,
    result      TEXT,
    ordinal     BIGINT  GENERATED ALWAYS AS IDENTITY
);

-- What listing asks for: one person's runs, in order.
CREATE INDEX work_runs_by_owner ON work_runs (owner, ordinal);
