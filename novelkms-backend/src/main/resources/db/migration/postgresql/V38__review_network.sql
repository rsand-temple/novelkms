-- ===========================================================================
-- V38 — Human Review Network (Phase 1)
--
-- The whole Phase 1 schema lands in one migration even though the feature ships
-- in six slices (profiles -> publish -> queue -> review -> metrics -> safety).
-- One migration means no schema chasing across slices and no half-built tables
-- in production between them.
--
-- This is the first legitimate CROSS-USER read path in NovelKMS. Every other
-- table is owned by exactly one user and guarded by TenantAuthorizationFilter,
-- which walks path UUIDs and denies anything the caller does not own. That
-- filter's segment switch returns `default -> true`, so paths under /api/review
-- pass through it untouched — authorization for these tables is therefore
-- enforced explicitly in the service layer (ReviewAccessService), NOT by the
-- tenant filter. The live manuscript stays fully isolated; only the frozen
-- snapshot below is ever exposed to another user.
--
-- MUTABILITY IS THE ORGANIZING RULE:
--   review_request      mutable, lifecycle-bearing (title, visibility, status)
--   review_snapshot     immutable, frozen at publish; never updated
--   review_context_item immutable, frozen with its snapshot
--   human_review        mutable while DRAFT, immutable once SUBMITTED
-- A submitted review stays attached to the exact snapshot its author read, even
-- after the source chapter is edited, trashed, or deleted.
--
-- review_snapshot.request_id carries UNIQUE for Phase 1: one request owns
-- exactly one snapshot, and a materially revised republish creates a NEW
-- request. Phase 2's snapshot-lineage model is reached by dropping this one
-- constraint — nothing else in the shape changes.
--
-- source_entity_id (on request and snapshot) is deliberately a bare UUID with
-- NO foreign key. It is provenance only. A snapshot MUST survive its source
-- chapter being deleted; an FK would either block the delete or cascade away
-- review history.
--
-- Contribution metrics (§13.2) are DERIVED, never counted into columns:
-- words-reviewed = SUM(review_snapshot.word_count) over that user's SUBMITTED
-- human_review rows. That definition is self-deduping — a package contributes
-- at most once per reviewer — so no read-tracking or view log is needed, and
-- withdrawing a review removes it from the metric automatically.
--
-- review_context_item is created now but unused in Phase 1 (context packages
-- are a Phase 2 surface). Creating it here avoids a later migration that would
-- have to retrofit rows onto already-immutable snapshots.
--
-- Handles follow the artifact case rule: `handle` preserves the casing the user
-- typed, `handle_lower` drives case-insensitive uniqueness. Unlike artifact
-- names this CAN be a DB unique index — there is no trash/soft-delete to make
-- a filtered index necessary.
--
-- All types here (UUID, VARCHAR, TEXT, INT, BOOLEAN, TIMESTAMP, REFERENCES ...
-- ON DELETE CASCADE) are valid in both H2 (MODE=PostgreSQL) and PostgreSQL, so
-- this file is identical to the postgresql dialect file.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Public identity. Opt-in: a user has no row until they claim a handle. A
-- handle is the gate for ALL participation, authoring and reviewing alike.
-- status SUSPENDED is the moderation lever (Phase 1F); it hides the profile and
-- blocks participation without deleting review history.
-- ---------------------------------------------------------------------------
CREATE TABLE review_profile (
    id              UUID         NOT NULL PRIMARY KEY,
    user_id         UUID         NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    handle          VARCHAR(24)  NOT NULL,
    handle_lower    VARCHAR(24)  NOT NULL UNIQUE,
    display_name    VARCHAR(120),
    bio             TEXT,
    genres_written  VARCHAR(400),
    genres_reviewed VARCHAR(400),
    visibility      VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- The mutable publication object. Editable while DRAFT/OPEN; the manuscript it
-- points at is not.
-- ---------------------------------------------------------------------------
CREATE TABLE review_request (
    id               UUID         NOT NULL PRIMARY KEY,
    author_user_id   UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_scope     VARCHAR(16)  NOT NULL,
    source_entity_id UUID         NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    author_questions TEXT,
    genre            VARCHAR(60),
    feedback_types   VARCHAR(400),
    content_warnings TEXT,
    visibility       VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    max_reviews      INT,
    published_at     TIMESTAMP,
    closes_at        TIMESTAMP,
    closed_at        TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_review_request_author ON review_request(author_user_id);
CREATE INDEX ix_review_request_queue  ON review_request(status, visibility, published_at);

-- ---------------------------------------------------------------------------
-- The immutable frozen manuscript. Written once at publish, never updated.
-- Titles are denormalized so the reviewer's view survives source deletion.
-- ---------------------------------------------------------------------------
CREATE TABLE review_snapshot (
    id               UUID         NOT NULL PRIMARY KEY,
    request_id       UUID         NOT NULL UNIQUE REFERENCES review_request(id) ON DELETE CASCADE,
    source_scope     VARCHAR(16)  NOT NULL,
    source_entity_id UUID         NOT NULL,
    source_title     VARCHAR(300) NOT NULL,
    book_title       VARCHAR(300),
    project_title    VARCHAR(300),
    content_html     TEXT         NOT NULL,
    word_count       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- Snapshotted context (synopsis, prior-chapter summaries, Codex entries).
-- Table exists from Phase 1; populated from Phase 2 onward.
-- ---------------------------------------------------------------------------
CREATE TABLE review_context_item (
    id               UUID         NOT NULL PRIMARY KEY,
    snapshot_id      UUID         NOT NULL REFERENCES review_snapshot(id) ON DELETE CASCADE,
    item_type        VARCHAR(32)  NOT NULL,
    title            VARCHAR(300),
    content_html     TEXT,
    display_order    INT          NOT NULL DEFAULT 0,
    source_entity_id UUID,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_review_context_snapshot ON review_context_item(snapshot_id, display_order);

-- ---------------------------------------------------------------------------
-- Reviewer <-> request relationship. Phase 1 has NO claim gate and NO
-- exclusivity: opening a public package creates an OPENED assignment, any
-- number of reviewers may hold one concurrently, and nothing expires. INVITED
-- exists for private invitations, where the assignment precedes any reviewer
-- action. This deliberately sidesteps the abandoned-claim/timeout problem.
-- ---------------------------------------------------------------------------
CREATE TABLE review_assignment (
    id                 UUID        NOT NULL PRIMARY KEY,
    request_id         UUID        NOT NULL REFERENCES review_request(id) ON DELETE CASCADE,
    reviewer_user_id   UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    state              VARCHAR(16) NOT NULL DEFAULT 'OPENED',
    invited_by_user_id UUID        REFERENCES app_user(id),
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at          TIMESTAMP,
    CONSTRAINT uq_review_assignment UNIQUE (request_id, reviewer_user_id)
);

CREATE INDEX ix_review_assignment_reviewer ON review_assignment(reviewer_user_id);

-- ---------------------------------------------------------------------------
-- The review itself. snapshot_id is redundant with request_id in Phase 1 (one
-- snapshot per request) but is stored anyway: it is what makes "the review is
-- permanently attached to the text the reviewer actually read" true by
-- construction once Phase 2 introduces snapshot lineage.
--
-- ai_assisted is a reviewer self-disclosure flag (§30.2 Q15/Q16). A policy with
-- nowhere to record the answer is unenforceable, so the column exists from day
-- one even though the surrounding policy is still terms-level.
-- ---------------------------------------------------------------------------
CREATE TABLE human_review (
    id               UUID        NOT NULL PRIMARY KEY,
    request_id       UUID        NOT NULL REFERENCES review_request(id) ON DELETE CASCADE,
    snapshot_id      UUID        NOT NULL REFERENCES review_snapshot(id) ON DELETE CASCADE,
    reviewer_user_id UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    visibility       VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    content_html     TEXT,
    word_count       INT         NOT NULL DEFAULT 0,
    ai_assisted      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at     TIMESTAMP,
    withdrawn_at     TIMESTAMP,
    CONSTRAINT uq_human_review UNIQUE (request_id, reviewer_user_id)
);

CREATE INDEX ix_human_review_reviewer ON human_review(reviewer_user_id, status);
CREATE INDEX ix_human_review_request  ON human_review(request_id, status);
CREATE INDEX ix_human_review_snapshot ON human_review(snapshot_id);

-- ---------------------------------------------------------------------------
-- Blocking is directional and enforced in BOTH directions at read time: if
-- either party has blocked the other, neither sees the other's requests,
-- reviews, or profile.
-- ---------------------------------------------------------------------------
CREATE TABLE user_block (
    id              UUID      NOT NULL PRIMARY KEY,
    blocker_user_id UUID      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    blocked_user_id UUID      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_block UNIQUE (blocker_user_id, blocked_user_id)
);

CREATE INDEX ix_user_block_blocked ON user_block(blocked_user_id);

-- ---------------------------------------------------------------------------
-- Moderation reports. target_id is a bare UUID (no FK) because it may point at
-- a request, review, profile, or user; the report must outlive the reported
-- object's removal so a dispute can still be adjudicated.
-- ---------------------------------------------------------------------------
CREATE TABLE content_report (
    id                  UUID        NOT NULL PRIMARY KEY,
    reporter_user_id    UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    target_type         VARCHAR(24) NOT NULL,
    target_id           UUID        NOT NULL,
    reason              VARCHAR(48) NOT NULL,
    detail              TEXT,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolved_by_user_id UUID        REFERENCES app_user(id),
    resolution_note     TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at         TIMESTAMP
);

CREATE INDEX ix_content_report_status ON content_report(status, created_at);
CREATE INDEX ix_content_report_target ON content_report(target_type, target_id);
