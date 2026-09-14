-- A database at version 2, holding the rows seed.sql held while 2 was the newest
-- version. Generated, not written by hand:
--
--     ./gradlew --no-daemon :backend:dump-database:writeMigrationFixture
--
-- Kept once a newer version exists: it is where databases still at 2 migrate from.

--
-- PostgreSQL database dump
--

\restrict migrationfixture

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: counters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.counters (
    id text NOT NULL,
    count bigint NOT NULL,
    ordinal bigint NOT NULL
);


--
-- Name: counters_ordinal_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.counters ALTER COLUMN ordinal ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.counters_ordinal_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: todos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.todos (
    id text NOT NULL,
    owner text NOT NULL,
    title text NOT NULL,
    done boolean NOT NULL,
    ordinal bigint NOT NULL
);


--
-- Name: todos_ordinal_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.todos ALTER COLUMN ordinal ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.todos_ordinal_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: counters; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.counters (id, count, ordinal) FROM stdin;
00000000-0000-4000-8000-000000000000	0	1
7f3c2d1e-9b8a-4c6d-8e5f-4a3b2c1d0e9f	9223372036854775807	2
b1e2c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e	-9223372036854775808	3
not-a-uuid-ü-ñ-漢字	42	4
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	create counters	SQL	V1__create_counters.sql	1952171312	test	2026-09-14 19:33:14.414442	8	t
2	2	create todos	SQL	V2__create_todos.sql	1990203018	test	2026-09-14 19:33:14.438989	5	t
\.


--
-- Data for Name: todos; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.todos (id, owner, title, done, ordinal) FROM stdin;
0f8d6c1e-2b3a-4c5d-8e7f-9a0b1c2d3e4f	someone@medusa.software	Water the plants	f	1
1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d	someone@medusa.software	Already done	t	2
2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e	someone-else@medusa.software	Zażółć gęślą jaźń 漢字 ✓	f	3
3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f	someone-else@medusa.software	long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long 	t	4
\.


--
-- Name: counters_ordinal_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.counters_ordinal_seq', 4, true);


--
-- Name: todos_ordinal_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.todos_ordinal_seq', 4, true);


--
-- Name: counters counters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.counters
    ADD CONSTRAINT counters_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: todos todos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todos
    ADD CONSTRAINT todos_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: todos_by_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX todos_by_owner ON public.todos USING btree (owner, ordinal);


--
-- PostgreSQL database dump complete
--

\unrestrict migrationfixture

