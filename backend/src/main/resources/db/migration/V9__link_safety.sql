-- Link safety: per-link flag, per-host verdict cache, Web Risk budget, admin domain blocks and user reports.
-- Ids carry their creation time (UUIDv7), so none of these tables has a created_at.

CREATE TYPE link_block_source AS ENUM ('BLOCKLIST', 'WEB_RISK', 'SHORTENER', 'ADMIN', 'ADMIN_DOMAIN');
CREATE TYPE link_host_verdict AS ENUM ('SAFE', 'UNSAFE');
CREATE TYPE link_report_reason AS ENUM ('MALWARE_PHISHING', 'SCAM', 'ILLEGAL', 'CHILD_ABUSE', 'ADULT', 'OTHER');
CREATE TYPE link_report_status AS ENUM ('OPEN', 'DISMISSED', 'ACTIONED', 'ESCALATED');

-- host is backfilled by the startup rescan; blocked_by is set by rescans and admin decisions only.
ALTER TABLE wish_links ADD COLUMN host varchar(253) NULL;
ALTER TABLE wish_links ADD COLUMN blocked_by link_block_source NULL;
CREATE INDEX idx_wish_links_host ON wish_links (host);

-- V7 left legacy rows unchecked; such rows only exist in dev data.
DELETE FROM wish_links WHERE url !~* '^https?://';
ALTER TABLE wish_links VALIDATE CONSTRAINT chk_wish_links_url_scheme;

CREATE TABLE link_host_checks (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    host       varchar(253) NOT NULL UNIQUE,
    verdict    link_host_verdict NOT NULL,
    checked_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_link_host_checks_checked_at ON link_host_checks (checked_at);

CREATE TABLE web_risk_usage (
    month date PRIMARY KEY,
    calls integer NOT NULL DEFAULT 0
);

-- Reports outlive the reporter, the link and the owner (FK SET NULL): an escalated report keeps its
-- evidence snapshot for the authorities (retention under GDPR Art. 17(3)(e), legal claims).
CREATE TABLE link_reports (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    wish_links_id  uuid NULL REFERENCES wish_links (id) ON UPDATE CASCADE ON DELETE SET NULL,
    users_id       uuid NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL,
    reason         link_report_reason NOT NULL,
    comment        varchar(500) NULL,
    status         link_report_status NOT NULL DEFAULT 'OPEN',
    -- no FK: the decision must survive the purge of the admin account
    admin_users_id uuid NULL,
    decided_at     timestamptz NULL,
    evidence       jsonb NULL,
    CONSTRAINT uq_link_reports_link_user UNIQUE (wish_links_id, users_id)
);
CREATE INDEX idx_link_reports_users ON link_reports (users_id);

CREATE TABLE blocked_domains (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    host            varchar(253) NOT NULL UNIQUE,
    admin_users_id  uuid NULL,
    link_reports_id uuid NULL REFERENCES link_reports (id) ON UPDATE CASCADE ON DELETE SET NULL
);

-- New values cannot be used inside this migration.
ALTER TYPE admin_audit_action ADD VALUE 'LINK_REPORT_DECISION';
ALTER TYPE admin_audit_action ADD VALUE 'LINK_REPORT_EXPORT';
ALTER TYPE admin_audit_action ADD VALUE 'DOMAIN_UNBLOCK';
ALTER TYPE admin_audit_action ADD VALUE 'LINK_UNBLOCK';
