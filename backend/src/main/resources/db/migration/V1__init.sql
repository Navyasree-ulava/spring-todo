-- Initial schema for the to-do app.
-- Flyway runs this once and tracks it in the flyway_schema_history table.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255)     NOT NULL UNIQUE,
    -- NULL for accounts that only ever sign in with Google
    password_hash   VARCHAR(255),
    display_name    VARCHAR(255)     NOT NULL,
    avatar_url      VARCHAR(1000),
    -- Guards account linking: we only auto-link on a VERIFIED email
    email_verified  BOOLEAN          NOT NULL DEFAULT FALSE,
    auth_provider   VARCHAR(20)      NOT NULL DEFAULT 'LOCAL',
    google_id       VARCHAR(255),
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE TABLE todos (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(255)    NOT NULL,
    description  TEXT,
    status       VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    due_date     TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Every query is scoped by user, so these matter a lot as the table grows
CREATE INDEX idx_todos_user_id       ON todos(user_id);
CREATE INDEX idx_todos_user_status   ON todos(user_id, status);
CREATE INDEX idx_todos_user_priority ON todos(user_id, priority);
CREATE INDEX idx_todos_due_date      ON todos(due_date);
CREATE INDEX idx_users_google_id     ON users(google_id);

-- Case-insensitive uniqueness on email
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));
