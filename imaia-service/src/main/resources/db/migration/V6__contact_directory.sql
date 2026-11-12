-- Entity layer over the message streams: contacts (friends and groups unify one
-- entity — thread_id IS contact.wxid) and group membership snapshots. The persona
-- column gives each conversation its own reply style, injected into the system
-- block per turn. Seeded from the archive so the directory starts populated.
CREATE TABLE contact (
    id            BIGSERIAL PRIMARY KEY,
    account_id    VARCHAR(128) NOT NULL,
    wxid          TEXT         NOT NULL,
    display_name  VARCHAR(256),
    kind          VARCHAR(16)  NOT NULL DEFAULT 'friend',
    remark        VARCHAR(256),
    persona       TEXT,
    muted         BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ,
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (account_id, wxid)
);

INSERT INTO contact (account_id, wxid, kind, first_seen_at, last_seen_at)
SELECT account_id, thread_id,
       CASE WHEN thread_id LIKE '%@chatroom' THEN 'group' ELSE 'friend' END,
       MIN(created_at), MAX(archived_at)
FROM wechat_message GROUP BY account_id, thread_id;

CREATE TABLE group_member (
    id               BIGSERIAL PRIMARY KEY,
    account_id       VARCHAR(128) NOT NULL,
    group_contact_id BIGINT       NOT NULL REFERENCES contact (id),
    member_wxid      TEXT         NOT NULL,
    display_name     VARCHAR(256),
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (group_contact_id, member_wxid)
);

INSERT INTO group_member (account_id, group_contact_id, member_wxid, display_name, last_seen_at)
SELECT DISTINCT ON (m.account_id, c.id, m.sender_id)
       m.account_id, c.id, m.sender_id, m.sender_name, m.archived_at
FROM wechat_message m
JOIN contact c ON c.account_id = m.account_id AND c.wxid = m.thread_id
WHERE m.thread_id LIKE '%@chatroom' AND m.sender_id IS NOT NULL
ORDER BY m.account_id, c.id, m.sender_id, m.archived_at DESC;