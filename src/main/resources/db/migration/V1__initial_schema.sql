-- ============================================================
-- MDM Complete Schema V1
-- Drops existing tables if present, then recreates everything
-- ============================================================

-- Drop all existing MDM tables (from any old schema) in safe order
DROP TABLE IF EXISTS audit_log                  CASCADE;
DROP TABLE IF EXISTS device_applications        CASCADE;
DROP TABLE IF EXISTS device_locations           CASCADE;
DROP TABLE IF EXISTS device_logs                CASCADE;
DROP TABLE IF EXISTS push_messages              CASCADE;
DROP TABLE IF EXISTS devices                    CASCADE;
DROP TABLE IF EXISTS remote_files               CASCADE;
DROP TABLE IF EXISTS application_settings       CASCADE;
DROP TABLE IF EXISTS configuration_applications CASCADE;
DROP TABLE IF EXISTS applications               CASCADE;
DROP TABLE IF EXISTS configurations             CASCADE;
DROP TABLE IF EXISTS groups                     CASCADE;
DROP TABLE IF EXISTS users                      CASCADE;
DROP TABLE IF EXISTS customers                  CASCADE;

-- Drop any old hmdm-server tables that may exist
DROP TABLE IF EXISTS configurationapplications  CASCADE;
DROP TABLE IF EXISTS applicationversions        CASCADE;
DROP TABLE IF EXISTS pluginsettings             CASCADE;
DROP TABLE IF EXISTS plugin_devicelog_settings_rules CASCADE;
DROP TABLE IF EXISTS plugin_devicelog_settings  CASCADE;
DROP TABLE IF EXISTS plugin_deviceinfo_settings CASCADE;
DROP TABLE IF EXISTS settings                   CASCADE;
DROP TABLE IF EXISTS userrolesettings           CASCADE;
DROP TABLE IF EXISTS permissions                CASCADE;
DROP TABLE IF EXISTS userroles                  CASCADE;
DROP TABLE IF EXISTS plugins                    CASCADE;
DROP TABLE IF EXISTS pendingpushes              CASCADE;
DROP TABLE IF EXISTS devicestatuses             CASCADE;
DROP TABLE IF EXISTS iconsize                   CASCADE;
DROP TABLE IF EXISTS kioskmode                  CASCADE;

-- ────────────────────────────────────────────────────────────
-- Create all tables fresh
-- ────────────────────────────────────────────────────────────

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    prefix      VARCHAR(50)  UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    login       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(200),
    email       VARCHAR(200),
    role        VARCHAR(50)  NOT NULL DEFAULT 'ADMIN',
    customer_id BIGINT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (customer_id, name)
);

CREATE TABLE configurations (
    id                   BIGSERIAL PRIMARY KEY,
    customer_id          BIGINT       NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    background_color     VARCHAR(20),
    text_color           VARCHAR(20),
    background_image_url VARCHAR(500),
    icon_size            INTEGER      DEFAULT 100,
    title                VARCHAR(50),
    display_status       BOOLEAN      DEFAULT FALSE,
    gps                  BOOLEAN,
    bluetooth            BOOLEAN,
    wifi                 BOOLEAN,
    mobile_data          BOOLEAN,
    kiosk_mode           BOOLEAN      DEFAULT FALSE,
    main_app             VARCHAR(200),
    lock_status_bar      BOOLEAN,
    system_update_type   INTEGER      DEFAULT 0,
    system_update_from   VARCHAR(10),
    system_update_to     VARCHAR(10),
    push_options         VARCHAR(50)  DEFAULT 'mqttWorker',
    keepalive_time       INTEGER      DEFAULT 300,
    request_updates      VARCHAR(50),
    disable_location     BOOLEAN      DEFAULT FALSE,
    app_permissions      VARCHAR(50),
    usb_storage          BOOLEAN,
    auto_brightness      BOOLEAN,
    brightness           INTEGER,
    manage_timeout       BOOLEAN,
    timeout_val          INTEGER,
    lock_volume          BOOLEAN,
    manage_volume        BOOLEAN,
    volume               INTEGER,
    password_mode        VARCHAR(50),
    time_zone            VARCHAR(100),
    orientation          INTEGER,
    kiosk_home           BOOLEAN,
    kiosk_recents        BOOLEAN,
    kiosk_notifications  BOOLEAN,
    kiosk_system_info    BOOLEAN,
    kiosk_keyguard       BOOLEAN,
    kiosk_lock_buttons   BOOLEAN,
    kiosk_screen_on      BOOLEAN,
    lock_safe_settings   BOOLEAN      DEFAULT FALSE,
    permissive           BOOLEAN      DEFAULT FALSE,
    kiosk_exit           BOOLEAN      DEFAULT FALSE,
    disable_screenshots  BOOLEAN      DEFAULT FALSE,
    autostart_foreground BOOLEAN      DEFAULT FALSE,
    show_wifi            BOOLEAN      DEFAULT FALSE,
    restrictions         TEXT,
    custom1              VARCHAR(500),
    custom2              VARCHAR(500),
    custom3              VARCHAR(500),
    password             VARCHAR(200),
    new_server_url       VARCHAR(500),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE applications (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    pkg          VARCHAR(200) NOT NULL,
    version      VARCHAR(100),
    version_code INTEGER,
    url          VARCHAR(1000),
    type         VARCHAR(20)  NOT NULL DEFAULT 'app',
    system       BOOLEAN      DEFAULT FALSE,
    file_path    VARCHAR(500),
    icon         TEXT,
    description  TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE configuration_applications (
    id                BIGSERIAL PRIMARY KEY,
    configuration_id  BIGINT  NOT NULL REFERENCES configurations(id) ON DELETE CASCADE,
    application_id    BIGINT  NOT NULL REFERENCES applications(id)   ON DELETE CASCADE,
    show_icon         BOOLEAN DEFAULT TRUE,
    remove            BOOLEAN DEFAULT FALSE,
    run_after_install BOOLEAN DEFAULT FALSE,
    run_at_boot       BOOLEAN DEFAULT FALSE,
    skip_version      BOOLEAN DEFAULT FALSE,
    use_kiosk         BOOLEAN DEFAULT FALSE,
    icon_text         VARCHAR(100),
    screen_order      INTEGER,
    key_code          INTEGER,
    bottom            BOOLEAN DEFAULT FALSE,
    long_tap          BOOLEAN DEFAULT FALSE,
    intent            VARCHAR(500),
    UNIQUE (configuration_id, application_id)
);

CREATE TABLE application_settings (
    id               BIGSERIAL PRIMARY KEY,
    configuration_id BIGINT       NOT NULL REFERENCES configurations(id) ON DELETE CASCADE,
    package_id       VARCHAR(200) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    value            TEXT,
    type             INTEGER      DEFAULT 1,
    last_update      BIGINT,
    read_only        BOOLEAN      DEFAULT FALSE,
    is_variable      BOOLEAN      DEFAULT FALSE,
    UNIQUE (configuration_id, package_id, name)
);

CREATE TABLE remote_files (
    id               BIGSERIAL PRIMARY KEY,
    configuration_id BIGINT       NOT NULL REFERENCES configurations(id) ON DELETE CASCADE,
    path             VARCHAR(500) NOT NULL,
    url              VARCHAR(1000),
    description      VARCHAR(500),
    checksum         VARCHAR(100),
    remove           BOOLEAN      DEFAULT FALSE,
    UNIQUE (configuration_id, path)
);

CREATE TABLE devices (
    id               BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT       NOT NULL,
    number           VARCHAR(200) NOT NULL,
    description      VARCHAR(500),
    group_id         BIGINT       REFERENCES groups(id),
    config_id        BIGINT       REFERENCES configurations(id),
    model            VARCHAR(200),
    imei             VARCHAR(50),
    imei2            VARCHAR(50),
    phone            VARCHAR(50),
    phone2           VARCHAR(50),
    iccid            VARCHAR(50),
    iccid2           VARCHAR(50),
    imsi             VARCHAR(50),
    imsi2            VARCHAR(50),
    serial           VARCHAR(100),
    cpu              VARCHAR(200),
    android_version  VARCHAR(20),
    battery_level    INTEGER,
    battery_charging VARCHAR(20),
    mdm_mode         BOOLEAN      DEFAULT FALSE,
    kiosk_mode       BOOLEAN      DEFAULT FALSE,
    default_launcher BOOLEAN      DEFAULT FALSE,
    launcher_type    VARCHAR(50),
    launcher_package VARCHAR(200),
    ip_address       VARCHAR(50),
    external_ip      VARCHAR(50),
    lat              DOUBLE PRECISION,
    lon              DOUBLE PRECISION,
    location_ts      BIGINT,
    custom1          VARCHAR(500),
    custom2          VARCHAR(500),
    custom3          VARCHAR(500),
    status           VARCHAR(20)  DEFAULT 'PENDING',
    last_sync        TIMESTAMP,
    enrolled_at      TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (customer_id, number)
);

CREATE TABLE device_logs (
    id         BIGSERIAL PRIMARY KEY,
    device_id  BIGINT    NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    log_time   TIMESTAMP NOT NULL,
    severity   INTEGER   NOT NULL,
    tag        VARCHAR(100),
    message    TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE device_locations (
    id         BIGSERIAL        PRIMARY KEY,
    device_id  BIGINT           NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    lat        DOUBLE PRECISION NOT NULL,
    lon        DOUBLE PRECISION NOT NULL,
    ts         BIGINT           NOT NULL,
    created_at TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE push_messages (
    id           BIGSERIAL PRIMARY KEY,
    device_id    BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    message_type VARCHAR(100) NOT NULL,
    payload      TEXT,
    sent         BOOLEAN      DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at      TIMESTAMP
);

CREATE TABLE device_applications (
    id           BIGSERIAL PRIMARY KEY,
    device_id    BIGINT       NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    pkg          VARCHAR(200) NOT NULL,
    name         VARCHAR(200),
    version      VARCHAR(100),
    version_code INTEGER,
    installed    BOOLEAN      DEFAULT TRUE,
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (device_id, pkg)
);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_login  VARCHAR(100),
    action      VARCHAR(200) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   BIGINT,
    detail      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_devices_customer    ON devices(customer_id);
CREATE INDEX idx_devices_number      ON devices(number);
CREATE INDEX idx_devices_config      ON devices(config_id);
CREATE INDEX idx_device_logs_device  ON device_logs(device_id);
CREATE INDEX idx_device_locs_device  ON device_locations(device_id);
CREATE INDEX idx_push_device         ON push_messages(device_id, sent);
CREATE INDEX idx_apps_customer       ON applications(customer_id);
CREATE INDEX idx_conf_apps_conf      ON configuration_applications(configuration_id);
CREATE INDEX idx_dev_apps_device     ON device_applications(device_id);

-- ────────────────────────────────────────────────────────────
-- Seed data
-- ────────────────────────────────────────────────────────────

-- Default customer
INSERT INTO customers (id, name, prefix) VALUES (1, 'Default', 'default');
SELECT setval('customers_id_seq', 1, true);

-- Admin user: Sravan / Sravan@123 (BCrypt cost=12)
INSERT INTO users (login, password, name, email, role, customer_id, active, created_at, updated_at)
VALUES (
    'Sravan',
    '$2a$12$uE3Kly1YeFG1jCHYJ2/7xeJvLZlsrFz.aVJFISwR7N9fW3qyEimqy',
    'Sravan Admin',
    'admin@mdm.local',
    'SUPER_ADMIN',
    1,
    true,
    NOW(), NOW()
);

-- Default configurations
INSERT INTO configurations (customer_id, name, description, kiosk_mode, push_options, keepalive_time, permissive, kiosk_exit, system_update_type, created_at, updated_at)
VALUES
(1, 'Managed Launcher',       'Shows app icons defined by admin.',          false, 'mqttWorker', 300, false, true, 0, NOW(), NOW()),
(1, 'Background Agent Mode',  'MDM in background; user has full control.',  false, 'mqttWorker', 300, true,  true, 0, NOW(), NOW()),
(1, 'Kiosk Mode',             'Lock device to a single application.',       true,  'mqttWorker', 300, false, true, 0, NOW(), NOW());

-- Sample device
INSERT INTO devices (customer_id, number, description, status, created_at, updated_at)
VALUES (1, 'h0001', 'My first Android device', 'PENDING', NOW(), NOW());

-- System apps
INSERT INTO applications (customer_id, name, pkg, type, system, created_at, updated_at) VALUES
(1, 'Headwind MDM',         'com.hmdm.launcher',                   'app', false, NOW(), NOW()),
(1, 'Headwind MDM Pager',   'com.hmdm.pager',                      'app', false, NOW(), NOW()),
(1, 'Chrome Browser',       'com.android.chrome',                   'app', true,  NOW(), NOW()),
(1, 'Google Maps',          'com.google.android.apps.maps',         'app', true,  NOW(), NOW()),
(1, 'Gmail',                'com.google.android.gm',                'app', true,  NOW(), NOW()),
(1, 'YouTube',              'com.google.android.youtube',           'app', true,  NOW(), NOW()),
(1, 'Google Drive',         'com.google.android.apps.docs',         'app', true,  NOW(), NOW()),
(1, 'Google Meet',          'com.google.android.apps.meetings',     'app', true,  NOW(), NOW()),
(1, 'Calculator (Google)',  'com.google.android.calculator',        'app', true,  NOW(), NOW()),
(1, 'Calendar (Google)',    'com.google.android.calendar',          'app', true,  NOW(), NOW()),
(1, 'Photos (Google)',      'com.google.android.apps.photos',       'app', true,  NOW(), NOW()),
(1, 'Contacts',             'com.android.contacts',                 'app', true,  NOW(), NOW()),
(1, 'Phone',                'com.android.dialer',                   'app', true,  NOW(), NOW()),
(1, 'Messaging',            'com.android.mms',                      'app', true,  NOW(), NOW()),
(1, 'Camera',               'com.android.camera2',                  'app', true,  NOW(), NOW()),
(1, 'Settings',             'com.android.settings',                 'app', true,  NOW(), NOW()),
(1, 'Samsung Camera',       'com.sec.android.app.camera',           'app', true,  NOW(), NOW()),
(1, 'Samsung Browser',      'com.sec.android.app.sbrowser',         'app', true,  NOW(), NOW()),
(1, 'Samsung Dialer',       'com.samsung.android.dialer',           'app', true,  NOW(), NOW()),
(1, 'File Manager (Google)','com.google.android.apps.nbu.files',    'app', true,  NOW(), NOW()),
(1, 'System UI',            'com.android.systemui',                 'app', true,  NOW(), NOW()),
(1, 'Bluetooth',            'com.android.bluetooth',                'app', true,  NOW(), NOW()),
(1, 'Google Services',      'com.google.android.gms',               'app', true,  NOW(), NOW()),
(1, 'Google Messaging',     'com.google.android.apps.messaging',    'app', true,  NOW(), NOW()),
(1, 'Google Contacts',      'com.google.android.contacts',          'app', true,  NOW(), NOW()),
(1, 'Google Dialer',        'com.google.android.dialer',            'app', true,  NOW(), NOW()),
(1, 'Pixel Camera',         'com.google.android.GoogleCamera',      'app', true,  NOW(), NOW()),
(1, 'Huawei Camera',        'com.huawei.camera',                    'app', true,  NOW(), NOW());
