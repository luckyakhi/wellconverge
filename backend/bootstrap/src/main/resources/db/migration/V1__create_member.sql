-- Membership context: the member table (Iteration 1).
CREATE TABLE member (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) NOT NULL UNIQUE,
    full_name      VARCHAR(120) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    goals          VARCHAR(255),
    date_of_birth  DATE,
    registered_at  TIMESTAMPTZ  NOT NULL,
    onboarded_at   TIMESTAMPTZ
);

CREATE INDEX idx_member_email ON member (email);
