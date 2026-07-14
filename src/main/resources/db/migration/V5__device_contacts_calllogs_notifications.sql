-- ============================================================
-- V5: Device Contacts, Call Logs & Notifications
-- ============================================================

-- Drop tables if they already exist (makes V5 safe to re-run)
DROP TABLE IF EXISTS device_notifications CASCADE;
DROP TABLE IF EXISTS device_contacts      CASCADE;
DROP TABLE IF EXISTS call_logs            CASCADE;

CREATE TABLE device_contacts (
    id          BIGSERIAL    PRIMARY KEY,
    device_id   BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name        VARCHAR(500),
    phone       VARCHAR(100),
    phone_type  VARCHAR(50),
    email       VARCHAR(500),
    raw_contact_id VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (device_id, raw_contact_id)
);

CREATE TABLE call_logs (
    id            BIGSERIAL    PRIMARY KEY,
    device_id     BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    phone_number  VARCHAR(100),
    call_type     VARCHAR(20)  NOT NULL,  -- INCOMING, OUTGOING, MISSED
    duration_sec  INTEGER      DEFAULT 0,
    call_date     BIGINT       NOT NULL,  -- timestamp in millis
    contact_name  VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE device_notifications (
    id          BIGSERIAL    PRIMARY KEY,
    device_id   BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    package_name VARCHAR(500),
    app_name    VARCHAR(500),
    title       TEXT,
    text        TEXT,
    received_at BIGINT       NOT NULL,  -- timestamp in millis
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes for fast lookups
CREATE INDEX idx_device_contacts_device   ON device_contacts(device_id);
CREATE INDEX idx_call_logs_device         ON call_logs(device_id);
CREATE INDEX idx_device_notifs_device     ON device_notifications(device_id);
CREATE INDEX idx_call_logs_date           ON call_logs(call_date DESC);
CREATE INDEX idx_device_notifs_date       ON device_notifications(received_at DESC);
