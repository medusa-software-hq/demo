-- The rows a newly written migration test database holds, fitting the tables as the newest migration
-- leaves them. A migration that changes the tables changes this too, with rows for what it added.
--
-- Chosen for what tends to break a migration rather than for what is typical: both ends of the
-- count's range, zero, and ids the service would never have generated itself.
INSERT INTO counters (id, count) VALUES
    ('00000000-0000-4000-8000-000000000000', 0),
    ('7f3c2d1e-9b8a-4c6d-8e5f-4a3b2c1d0e9f', 9223372036854775807),
    ('b1e2c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e', -9223372036854775808),
    ('not-a-uuid-ü-ñ-漢字', 42);

-- Todos belonging to two people, so a migration that loses track of whose is whose shows. A long
-- title, and one outside ASCII.
INSERT INTO todos (id, owner, title, done) VALUES
    ('0f8d6c1e-2b3a-4c5d-8e7f-9a0b1c2d3e4f', 'someone@medusa.software', 'Water the plants', FALSE),
    ('1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', 'someone@medusa.software', 'Already done', TRUE),
    ('2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', 'someone-else@medusa.software', 'Zażółć gęślą jaźń 漢字 ✓', FALSE),
    ('3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f', 'someone-else@medusa.software', repeat('long ', 200), TRUE);

-- Work runs, since version 3: one finished, one caught halfway through, and somebody else's that
-- has not got past its first step.
INSERT INTO work_runs (id, owner, steps_total, steps_done, result) VALUES
    ('4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f80', 'someone@medusa.software', 6, 6, 'Did 6 steps; the answer is 42'),
    ('5e6f7a8b-9c0d-4e1f-8a2b-4c5d6e7f8091', 'someone@medusa.software', 6, 3, NULL),
    ('6f7a8b9c-0d1e-4f2a-9b3c-5d6e7f809102', 'someone-else@medusa.software', 3, 0, NULL);
