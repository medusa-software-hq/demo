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
