# NovelKMS Human Review Network

## High-Level Design

**Status:** Conceptual design **Scope:** Product and architectural direction only **Implementation status:** Not scheduled or approved for development

## 1. Executive Summary

NovelKMS may add a human-review network through which authors can publish selected manuscript material and request reviews from other NovelKMS users.

An author could publish a book, part, chapter, or scene—most commonly a chapter—as a reviewable snapshot. Other users could browse an available-review queue, read published material, and submit feedback. Reviews could be public or private.

The initial system would use an honor-based reciprocity model rather than payments, ratings, or a formal credit economy. NovelKMS would publicly track simple contribution metrics such as:

- Manuscript words read
- Review words written
- Reviews completed
- Reviews received

The feature would begin as a focused manuscript-review exchange, not as a general-purpose social network. Social features such as handles, profiles, shared bios, avatars, project descriptions, public synopses, and shared Codex context would exist primarily to support trust, reviewer selection, and manuscript understanding.

Over time, the system could expand into structured beta reading, itemized editorial review, genre- or expertise-specific review requests, private reviewer invitations, context packages for later chapters, and deeper integration with the existing AI-review workflow.

## 2. Strategic Rationale

NovelKMS currently provides value primarily through software capabilities: manuscript management, structured project knowledge, AI-assisted review, summaries, editorials, import/export, and related authoring workflows.

A human-review network introduces a different form of value: access to other writers and readers.

This creates a potential network effect:

1. Authors initially join because NovelKMS is useful writing software.
2. Authors participate because they need manuscript feedback.
3. Authors remain engaged because they have built reciprocal relationships with reviewers and other writers.
4. The value of the platform increases as more reviewers and manuscripts become available.

This direction would differentiate NovelKMS from products that provide only editing, organization, or AI assistance. Human reactions—confusion, interest, emotional response, boredom, surprise, attachment to characters, and willingness to continue reading—cannot be fully replaced by automated editorial analysis.

## 3. Product Positioning

The feature should not be positioned as conventional social media.

The core interaction is not posting content to attract followers. It is requesting and providing useful manuscript feedback.

A more appropriate conceptual model is:

> A work-centered review community for novelists.

The network should emphasize:

- Manuscripts
- Review requests
- Constructive feedback
- Reciprocity
- Author-controlled disclosure
- Reviewer context
- Durable review records

It should avoid, especially in the initial release:

- General social feeds
- Follower counts
- Popularity rankings
- Likes or reaction counts
- Public arguments
- Engagement-maximizing notifications
- Influencer-style profiles
- Competitive reviewer ratings

## 4. Goals

The human-review network should:

1. Allow authors to request human feedback on selected manuscript material.
2. Make it easy for reviewers to discover work suited to their interests and available time.
3. Preserve the exact manuscript version reviewed.
4. Support both private and publicly visible feedback.
5. Encourage reciprocity without introducing a complex currency or marketplace.
6. Provide enough profile information for authors and reviewers to make informed choices.
7. Allow authors to expose only the contextual information needed for a review.
8. Integrate naturally with NovelKMS manuscript and review workflows.
9. Keep authors in control of their manuscript, identity, and shared project information.
10. Establish a foundation that can later support more structured review types.

## 5. Non-Goals for the Initial Release

The initial release should **not** attempt to provide:

- Paid reviews
- A reviewer marketplace
- Formal review credits or tokens
- Reviewer star ratings
- Public manuscript publication
- Follower or friend systems
- Direct messaging unrelated to a review
- Collaborative manuscript editing
- Inline manuscript rewriting by reviewers
- Item-by-item structured editorial findings
- Automated reviewer matching
- Community moderation at social-network scale
- Public comments on public reviews
- Open-ended posting or general discussion feeds

These capabilities could be reconsidered after the core review exchange has been validated.

## 6. Core Design Principles

### 6.1 Reviews revolve around work

Every interaction should originate from a manuscript review request or an existing review relationship.

Profiles and public project information exist to establish context and trust, not to create a separate attention economy.

### 6.2 Published material is a snapshot

Publishing material for review must create an immutable review snapshot.

The review must remain associated with the exact text the reviewer read, even if the author later edits or deletes the source manuscript.

The live manuscript must never be exposed directly through the review system.

### 6.3 Authors control disclosure

The author chooses:

- What manuscript scope is shared
- Who may see it
- Whether the request appears in the public queue
- Whether submitted reviews are public or private
- Which synopsis, summaries, and Codex entries accompany it
- Whether their identity is represented by a handle or a fuller profile
- When the request closes

### 6.4 Reciprocity should be visible but lightweight

The system should encourage contribution through transparent activity metrics rather than penalties, rankings, or virtual currency.

A user should be able to see whether another participant regularly gives reviews as well as receives them.

### 6.5 Public metrics should be objective

Initial public contribution metrics should be based on measurable activity:

- Words read
- Review words written
- Reviews completed
- Reviews received

NovelKMS should avoid subjective reputation scores until there is evidence they are necessary.

### 6.6 Human and AI reviews remain distinct

Human reviews and AI reviews may eventually appear within a unified review workspace, but they must retain clear provenance.

A human review must never be presented as an AI result, and an AI review must never be attributed to a human reviewer.

### 6.7 Late-book review should be practical

NovelKMS should use its existing summaries, memory documents, Codex records, and project knowledge to let reviewers understand later chapters without requiring them to read the entire preceding manuscript.

## 7. Primary User Roles

### 7.1 Author

The author owns the source manuscript and creates review requests.

The author can:

- Create a review package
- Select manuscript material
- Choose visibility and review settings
- Provide context
- Publish, pause, close, or withdraw a request
- Read submitted reviews
- Mark reviews as useful or acknowledged in a future phase
- Preserve reviews in project history

### 7.2 Reviewer

The reviewer discovers or receives access to a review request.

The reviewer can:

- Browse available requests
- Filter requests
- Open a review package
- Read shared context
- Read the manuscript snapshot
- Write and submit a review
- Choose whether their identity is shown where anonymity is allowed
- Review their own contribution history

A user may act as both author and reviewer.

### 7.3 Administrator or Moderator

Initially, administrative capabilities may be limited to:

- Investigating reported material
- Suspending abusive accounts
- Removing public visibility
- Reviewing audit history
- Resolving privacy or ownership disputes

A fuller moderation design should be created before broad public release.

## 8. Core Domain Concept: Review Package

A **Review Package** is the user-facing concept of "a piece of manuscript, published for human review." In the underlying model it is not a single object — it decomposes into two entities with different mutability rules, and keeping them distinct from the outset avoids a conflict that shows up almost immediately: request metadata (title, description, visibility, status) is naturally editable while a request is open, but the manuscript text under review must never change once a reviewer has read or reviewed it.

- **Review Request** — the mutable, lifecycle-bearing object: title, description, visibility, requested feedback type, author questions, open/paused/closed status, dates. This is the object whose lifecycle is defined in Section 9.
- **Manuscript Snapshot** — a strictly immutable, append-only copy of the selected manuscript content, frozen at the moment of publication. A Review Request references exactly one Manuscript Snapshot at a time.

A review package is not part of the live manuscript hierarchy. Both entities are separate publication artifacts derived from it, never a live view onto it.

For product/UI purposes, "Review Package" remains the umbrella term for a Review Request plus its current Manuscript Snapshot and any Context Items (Section 16). The split matters at the data-model level, not necessarily in author-facing language.

### 8.1 Supported source scopes

A package may be created from:

- Book
- Part
- Chapter
- Scene

Chapter should be the primary and recommended initial scope.

Book and part requests may later require special handling because of their length.

### 8.2 Review package contents

**Review Request fields** (mutable while the request is `DRAFT` or `OPEN`; some fields, such as visibility widening, may be restricted once reviews exist against the current snapshot):

- Package title
- Author handle
- Source scope type
- Source title
- Genre or category
- Short author description
- Review request description
- Requested feedback type
- Visibility
- Publication date
- Optional closing date
- Request status
- Optional author questions

**Manuscript Snapshot fields** (immutable once created):

- Frozen manuscript content
- Snapshot word count
- Snapshot creation timestamp
- Source entity identifier and version marker at capture time

**Context Item fields** (immutable once created; see Section 16):

- Optional book synopsis
- Optional prior-chapter summaries
- Optional selected Codex entries
- Optional content warnings
- Optional spoiler guidance

### 8.3 Snapshot behavior

When an author publishes a package:

1. NovelKMS copies the selected manuscript content into a new, immutable Manuscript Snapshot.
2. The Review Request records which Manuscript Snapshot it currently points to, and the source entity's identity and state at capture time.
3. Later manuscript edits do not alter any existing Manuscript Snapshot.
4. Submitted reviews remain permanently attached to the specific Manuscript Snapshot the reviewer read, not to the Review Request in the abstract.
5. Republishing revised material creates a new Manuscript Snapshot.

A revised submission should create a new Manuscript Snapshot linked to a new Review Request in Phase 1 — this is the simplest option and sidesteps the question of what happens to in-progress or submitted reviews against an older snapshot when a request is "revised in place." Whether a revised snapshot can instead be attached to the *same* Review Request (as a lineage of snapshots under one request, with each submitted review permanently tagged to the snapshot version it reviewed) is a reasonable Phase 2 refinement once the one-request-per-snapshot model has been validated. Explicit revision chains are not needed for Phase 1.

## 9. Review Request Lifecycle

Suggested lifecycle states:

- `DRAFT` — being prepared by the author
- `OPEN` — available for review
- `PAUSED` — temporarily unavailable to new reviewers
- `CLOSED` — no longer accepting reviews
- `WITHDRAWN` — removed by the author
- `REMOVED` — administratively removed

A request may remain readable to its author and existing reviewers after closing.

Public discovery should include only eligible `OPEN` requests.

## 10. Review Lifecycle

Suggested review states:

- `DRAFT` — reviewer has opened the package and is composing a review; visible only to the reviewer, not to the author, and excluded from public contribution metrics
- `SUBMITTED` — delivered to the author; the only state that counts toward metrics (Section 13.2)
- `WITHDRAWN` — removed by the reviewer before or after submission, subject to retention policy
- `REMOVED` — administratively removed

A `DRAFT` review is distinct from having merely opened or claimed a package (Section 24, Review Assignment) — a reviewer can open a package and never start writing, which should not be conflated with having begun a review. Phase 1 does not need to track that distinction precisely; it matters mainly for a future signal such as "requests with claims but no drafts," useful for diagnosing reviewer-supply problems (Section 29.1).

A later structured workflow could add author-facing states such as:

- Acknowledged
- Acted on
- Deferred
- Dismissed

Those states would mirror aspects of the existing AI-review triage model, but they are not necessary for the first release.

## 11. Public and Private Reviews

A review request and a submitted review have separate visibility concerns.

### 11.1 Request visibility

Potential request visibility modes:

- **Public queue** — discoverable by eligible NovelKMS users
- **Unlisted** — available only through a direct link or invitation
- **Private invitation** — available only to selected users

The initial release may support only public queue and private invitation if unlisted sharing adds unnecessary complexity.

### 11.2 Review visibility

Potential review visibility modes:

- **Private** — visible only to the author and reviewer
- **Public** — visible to other users permitted to view the package
- **Anonymous private** — author sees the review but not the reviewer identity
- **Anonymous public** — review is visible without reviewer identity

For safety and accountability, fully anonymous reviewing may be deferred. NovelKMS may instead hide the reviewer from the author while retaining identity internally for moderation.

The author should define which review visibility choices are permitted for a package. A reviewer should not be able to make a review public when the author requested private feedback only.

## 12. Review Queue

The Review Queue is the primary discovery surface.

Each queue entry should provide enough information to support a decision without exposing the manuscript itself.

Suggested queue information:

- Package title
- Author handle
- Genre
- Manuscript scope
- Word count
- Requested review type
- Short description
- Content warnings
- Publication age
- Number of reviews already received
- Whether the request permits public reviews
- Whether context documents are available

Potential filters:

- Genre
- Word-count range
- Scope type
- Review type
- Public or private review
- Newest
- Fewest reviews received
- Requests from users who have reviewed others
- Language
- Content-warning compatibility

Automated ranking should be avoided initially. A chronological queue with basic filters is more transparent.

## 13. Reciprocity and Contribution Metrics

The initial reciprocity model should remain informational and honor-based.

### 13.1 Public metrics

A profile may display:

- Total manuscript words reviewed
- Total review words written
- Reviews completed
- Reviews received
- Public reviews written
- Private reviews written
- Member since
- Recent review activity

### 13.2 Definitions

**Words reviewed** is defined precisely as: the sum of Manuscript Snapshot word counts across all packages for which the user has a review in `SUBMITTED` state. This definition is self-dedupling by construction — a package can be opened, re-opened, or drafted against any number of times, but it contributes to the metric at most once per user, at the moment a review reaches `SUBMITTED`. No separate "reading" event, view log, or read-tracking instrumentation is required to keep this metric accurate; the existing `SUBMITTED` review record is sufficient as the source of truth.

**Review words written** should be based on the submitted review text.

A review should count only after submission (i.e., `DRAFT` reviews are excluded, per Section 10).

### 13.3 Limitations

These metrics do not prove review quality.

A long review may be poor, while a short review may be insightful. The metrics are intended to show participation and reciprocity, not rank users.

### 13.4 Future options

Possible later additions include:

- Reviews given versus received
- Recent contribution period
- Review completion rate
- Author acknowledgements
- Private endorsements
- Genre experience

These should be introduced cautiously to avoid gaming and popularity effects.

### 13.5 Partial AI Book Reviews

If an author wants human reviews of chapter 15 of his book, he cannot realistically expect other users to read chapter 1-14 just to provide this review. This introduces the need of a new type of AI review - the partial book review. The author can instruct the AI to run a book summary on only chapter 1-14. The author can specify how long the summary should be, e.g. 1 page, 2 pages etc., such that it can be provided along with chapter 15 for the review.

## 14. Profiles and Identity

The review network requires a user-facing identity distinct from authentication credentials.

### 14.1 Profile fields

A minimal profile may include:

- Unique handle
- Display name, optional
- Avatar, optional
- Short biography
- Genres written
- Genres reviewed
- Preferred review types
- Public contribution metrics
- Current projects, optional
- Location at broad region level, optional
- Profile visibility settings

Email addresses and OAuth identities must never be publicly exposed.

### 14.2 Handles

Handles should:

- Be unique
- Be changeable under controlled rules
- Be case-insensitive for uniqueness
- Preserve display casing
- Avoid reserved system names
- Be subject to moderation

A handle history or stable internal user identifier may be required so review records remain attributable after handle changes.

### 14.3 Avatars

Avatars should initially support:

- Uploaded image
- Generated initials
- Default system avatar

External image URLs should not be embedded directly because of privacy, tracking, and availability concerns.

## 15. Public Project Information

Users may optionally publish limited information about their writing projects.

Possible profile sections include:

- What I am working on
- Current project title
- Genre
- Short pitch
- Project status
- Current manuscript word count
- Current chapter or phase
- Review requests currently open
- Book-level synopsis
- Series information
- Selected cover art

Public project information should be separate from the private project record.

The author should explicitly choose which fields are published. NovelKMS should not automatically expose project metadata.

## 16. Context Packages

A review package may include contextual material that helps the reviewer understand the manuscript selection.

Possible context sources:

- Book synopsis
- Chapter summaries
- Prior-chapter summaries
- Story-so-far document
- Character profiles
- Timeline entries
- Worldbuilding entries
- Glossary
- Voice sheets
- Selected canon entries
- Author-written review notes

### 16.1 Explicit sharing

Context must be shared explicitly.

The existence of a Codex entry does not make it available to reviewers.

The author chooses each context item or context category included in the package.

### 16.2 Snapshot behavior

Shared context should also be snapshotted when the package is published.

This ensures the reviewer sees the contextual information the author intended at that time.

### 16.3 Late-chapter review

For a later chapter, NovelKMS could assemble a review context package containing:

1. Book synopsis
2. Ordered summaries of preceding chapters
3. Relevant characters
4. Selected timeline or canon entries
5. The reviewable chapter snapshot

This may allow a reviewer to evaluate Chapter 30 without reading Chapters 1–29 in full.

The interface must clearly tell the reviewer that they are reviewing from summarized context rather than from a complete reading of the book.

## 17. Review Types

The initial release may use a simple free-text review request accompanied by optional categories.

Potential categories include:

- General reader reaction
- Pacing
- Character
- Dialogue
- Plot clarity
- Continuity
- Worldbuilding
- Tone
- Historical accuracy
- Sensitivity or subject-matter perspective
- Grammar and mechanics
- Line-level prose
- Developmental feedback
- Beta reading

The author should be able to specify:

- What feedback is wanted
- What feedback is not wanted
- Specific questions to answer

A reviewer should see these instructions before beginning.

Structured question-and-answer review forms may be added later.

## 18. Review Reading and Writing Experience

The review surface should present:

- Author instructions
- Shared context
- Manuscript snapshot
- Review editor
- Package metadata
- Review progress
- Save-draft behavior
- Submit action

The reviewer should be able to save an unfinished review privately.

The initial review itself may be a single rich-text document.

Inline annotations, comments anchored to manuscript text, and itemized findings should be deferred until the basic workflow is validated.

## 19. Relationship to Existing NovelKMS Review Features

Human review should ultimately be available alongside existing AI review artifacts while retaining distinct provenance.

A future review workspace might include:

- AI Reviews
- Human Reviews
- Editorials
- Review History

Human reviews should become durable project artifacts after submission.

The author may eventually triage individual human-review observations using states similar to:

- Open
- Done
- Dismissed
- Deferred

However, a human review should first be preserved as the reviewer submitted it. Author annotations and triage state should be stored separately from the original review.

## 20. Notifications

The initial notification model should be narrow and transactional.

Potential events:

- A requested reviewer was invited
- A review request was accepted or claimed
- A review was submitted
- A review request was closed
- A reviewer withdrew
- A package was removed
- A moderation action affected a user

The system should avoid engagement-oriented notifications such as popularity updates or repeated prompts to return.

Email preferences and in-app notification preferences should be user-controlled.

## 21. Trust, Safety, and Moderation

Introducing user-published manuscripts and interpersonal feedback creates significant new operational responsibilities.

The design must account for:

- Harassment
- Plagiarism
- Copyright infringement
- Hate speech
- Sexual or violent content
- Reviews written in bad faith
- Exposure of private or identifying information
- AI-generated spam reviews
- Retaliatory behavior
- Repeated unsolicited contact
- Users copying unpublished manuscript content

Minimum safety capabilities before public launch should include:

- Content reporting
- User blocking
- Review withdrawal
- Request withdrawal
- Administrative removal
- Account suspension
- Audit logging
- Terms governing manuscript access and review conduct
- Clear copyright ownership language
- Clear privacy controls

A private pilot with invited users would materially reduce initial moderation risk.

## 22. Copyright and Manuscript Protection

Authors retain ownership of all manuscript content.

Reviewers receive a limited right to access shared material solely for the requested review.

The system should clearly communicate that reviewers may not:

- Republish manuscript text
- Share the package with others
- Use the manuscript to train models
- Incorporate protected text into their own work
- Download or redistribute content except where explicitly permitted

Technical controls cannot fully prevent copying or screenshots. NovelKMS should be honest about this limitation.

Possible deterrents include:

- Visible reviewer handle watermark
- Package-specific access logging
- Disabled bulk export
- Limited copy behavior
- Explicit access terms
- Revocable access

These measures should be evaluated against accessibility and usability.

## 23. Privacy Model

The review network must keep private NovelKMS project data isolated from public profile and review data.

Key rules:

1. Private projects remain private by default.
2. Publishing a review package exposes only the selected snapshot and selected context.
3. Profile publication is opt-in.
4. Authentication email addresses remain private.
5. Review access is authorization-controlled.
6. Withdrawn material should no longer be available to new readers.
7. Submitted reviews and audit records may require retention even after withdrawal.
8. Public search engines should not index manuscript snapshots unless an author explicitly opts into a future public-publication mode.

The initial system should likely require authentication to view any manuscript review package.

### 23.1 Relationship to existing tenant isolation

NovelKMS's current multi-user model enforces strict ownership: a user may access only entities they own, and any cross-user access attempt returns 404 rather than 403, so object existence is never disclosed. The human-review network is, by design, the first legitimate cross-user read path in the system — a reviewer is intentionally granted access to another user's derived data (a Manuscript Snapshot and its Context Items).

This should be implemented as an additional, narrow authorization check — "does this user have an active Review Assignment or matching public-visibility grant against this specific Manuscript Snapshot/Context Item" — layered alongside the existing ownership-based authorization, not as a modification to it. The existing manuscript-tree authorization should remain untouched and continue to guarantee that the live manuscript, and anything not explicitly published as a snapshot, stays fully isolated. This boundary should be made explicit in the technical design before implementation begins.

## 24. High-Level Information Model

The eventual domain model may include concepts equivalent to:

### User Profile

Public identity and review preferences associated with an authenticated user.

### Public Project Profile

An explicitly published representation of selected project metadata.

### Review Request

The mutable, lifecycle-bearing publication object: title, description, visibility, status, and a reference to its current Manuscript Snapshot (Section 8).

### Manuscript Snapshot

An immutable, frozen copy of manuscript content captured at publication time. Referenced by a Review Request; never edited after creation (Section 8.3).

### Review Package Context Item

A snapshotted synopsis, summary, Codex entry, or other shared reference.

### Review Assignment

The relationship between a reviewer and a package. For a private invitation, the assignment begins in an `INVITED` state before the reviewer has taken any action. For a public-queue package, no invitation step exists — a reviewer simply opens the package, which is sufficient to create the assignment.

For Phase 1, "claiming" a package should be treated as unlimited and unlocked: any number of reviewers may open and review the same public package concurrently, with no reservation, exclusivity, or expiry mechanism (this resolves Design Questions 1 and 2 in Section 30). An author may optionally cap the number of accepted reviews on a request (Design Question 3), but the default is uncapped. This keeps Phase 1 free of the abandoned-claim/timeout problem that an exclusive-claim model would otherwise require solving immediately.

### Human Review

The submitted review document, visibility, timestamps, and reviewer attribution.

### Review Activity

Events used for audit, notifications, and contribution metrics.

### User Block

A relationship preventing interaction or visibility between users.

### Content Report

A moderation report concerning a package, profile, review, or user.

These are conceptual entities, not final database-table definitions.

## 25. Navigation and Product Surfaces

The review network should be separate from the manuscript navigation tree.

Possible top-level application surfaces:

- My Writing
- Review Queue
- My Review Requests
- Reviews I Am Writing
- Reviews Received
- My Profile

Within a project, an author may also have:

- Publish for Review
- Human Reviews
- Published Packages
- Public Project Profile

Review packages should not appear as manuscript children because they are derived snapshots rather than editable manuscript entities.

## 26. Suggested Initial User Flow

### 26.1 Author publishes a chapter

1. Author selects a chapter.
2. Author chooses **Publish for Human Review**.
3. NovelKMS displays package settings.
4. Author enters a title and review request.
5. Author selects visibility.
6. Author selects requested feedback categories.
7. Author optionally includes a synopsis, previous chapter summaries, and selected Codex entries.
8. NovelKMS previews exactly what reviewers will see.
9. Author publishes the package.
10. The package appears in the review queue or is sent to invited reviewers.

### 26.2 Reviewer completes a review

1. Reviewer opens the Review Queue.
2. Reviewer filters by genre, word count, and review type.
3. Reviewer opens a package summary.
4. Reviewer chooses to begin the review.
5. NovelKMS records the assignment.
6. Reviewer reads the context and manuscript snapshot.
7. Reviewer writes and saves feedback.
8. Reviewer submits the review.
9. Contribution metrics are updated.
10. The author is notified.

### 26.3 Author receives a review

1. Author receives a notification.
2. Author opens the submitted review.
3. NovelKMS displays the reviewed snapshot beside or linked to the review.
4. Author reads the feedback.
5. The review becomes part of the project’s human-review history.
6. In a later phase, the author may annotate or triage review observations.

## 27. Phased Product Strategy

### Phase 0: Validation

Before implementation:

- Interview existing NovelKMS users or prospective users.
- Validate willingness to review other writers.
- Determine acceptable manuscript lengths.
- Test whether public contribution metrics motivate reciprocity.
- Understand privacy and plagiarism concerns.
- Determine whether writers prefer open queues or invitation-based exchanges.

A manual or invitation-only pilot could validate the workflow without building a complete public network.

### Phase 1: Basic Review Exchange

Include:

- Handle and minimal profile
- Chapter snapshot publication
- Public or invited request
- Basic queue
- Single rich-text review
- Public or private review
- Words-reviewed and review-words-written metrics
- Request close or withdrawal
- Basic reporting and blocking
- Transactional notifications

Exclude:

- Inline annotations
- Ratings
- Credits
- Payments
- Followers
- Public feeds
- Complex matching
- Structured editorial findings

### Phase 2: Context and Discovery

Add:

- Synopsis sharing
- Previous-chapter summary packages
- Selected Codex sharing
- Better queue filters
- Genre and reviewer preferences
- Public project profiles
- What-I-am-working-on profile section
- Private reviewer invitations
- Review availability settings

### Phase 3: Structured Reviews

Add:

- Author-defined review questions
- Review templates
- Itemized findings
- Anchored comments
- Review categories
- Author triage workflow
- Human and AI review comparison
- Reviewer expertise metadata

### Phase 4: Community and Reputation

Only after evidence of need:

- Endorsements
- Trusted reviewer relationships
- Repeat-reviewer workflows
- Reading groups
- Private circles
- Curated review cohorts
- Moderated genre communities

Formal ratings, credits, or payments should require a separate product and abuse analysis.

## 28. Success Measures

Early success should be measured by useful review activity rather than account growth alone.

Potential measures include:

- Percentage of open requests receiving at least one review
- Median time to first review
- Review completion rate
- Median manuscript words per review
- Median review words submitted
- Percentage of review recipients who later review another user
- Repeat reviewer-author pairings
- Percentage of users who both give and receive reviews
- Rate of withdrawn or abandoned assignments
- Report and moderation rate
- Percentage of users returning to submit another request

A particularly important measure is reciprocity:

> How often does receiving a review lead a user to review someone else?

## 29. Principal Risks

### 29.1 Insufficient reviewer supply

Authors may submit more material than the community is willing to review.

Mitigations:

- Keep review scopes short
- Emphasize chapters over books
- Display word counts clearly
- Encourage reciprocity
- Highlight requests with few reviews
- Pilot with a small committed group

### 29.2 Low-quality or superficial reviews

Visible word metrics may encourage padding.

Mitigations:

- Make metrics descriptive rather than competitive
- Let authors specify questions
- Add private endorsements later
- Detect obvious spam
- Avoid leaderboards

### 29.3 Plagiarism and confidentiality concerns

Authors may be reluctant to share unpublished work.

Mitigations:

- Authenticated-only access
- Explicit sharing controls
- Snapshot-specific access
- Reviewer identity and access logging
- Clear terms
- Private invitations
- Watermarking where appropriate

### 29.4 Moderation burden

Even a focused work network can produce conflict and abuse.

Mitigations:

- Begin with a private pilot
- Limit initial communication surfaces
- Avoid public comments and feeds
- Add reporting and blocking before public launch
- Preserve administrative audit records

### 29.5 Product distraction

A social-review platform could consume substantially more engineering and operational effort than manuscript features.

Mitigations:

- Treat it as a separate product track
- Validate manually before implementation
- Build the smallest viable exchange
- Avoid broad social features
- Establish explicit go/no-go criteria after each phase

## 30. Design Questions

### 30.1 Resolved for Phase 1

These questions change the shape of the core data model and have been resolved so implementation planning can proceed. They can be revisited with evidence from the pilot.

1. **Must a reviewer claim a request before reading it?** No. Opening a public-queue package is itself sufficient to create a Review Assignment; there is no separate claim gate (Section 24).
2. **Can multiple reviewers claim the same request?** Yes, unlimited by default. Phase 1 uses no reservation, exclusivity, or claim-timeout mechanism (Section 24).
3. **Should authors limit the number of accepted reviews?** Optional per-request cap, uncapped by default (Section 24).
4. **How should revised versions of the same chapter be represented?** A materially revised republish creates a new Manuscript Snapshot under a new Review Request in Phase 1. Attaching a lineage of snapshots to a single, persistent Review Request is deferred to Phase 2 (Section 8.3).

### 30.2 Remaining Open Questions

The following questions should be resolved before detailed technical design of the affected areas:

1. Should full books be permitted initially?
2. Are anonymous reviews desirable or too risky?
3. Can reviewers download packages for offline reading?
4. Should copying manuscript text be restricted?
5. How long does access remain after a request closes?
6. Can a submitted review be edited?
7. Can a reviewer withdraw a submitted review?
8. What records must remain for audit and dispute handling?
9. Should contribution metrics include private reviews?
10. How should very short or clearly non-substantive reviews affect metrics?
11. Should authors be able to decline a reviewer?
12. Should users be able to restrict requests by genre or review history?
13. Should public reviews be readable by all authenticated users or only package participants?
14. What moderation tools are required before inviting users outside a controlled pilot?
15. Can AI-assisted reviews be submitted as human reviews?
16. Should NovelKMS require disclosure when a reviewer used AI assistance?

## 31. Recommended Direction

The concept is strategically promising, but it should be treated as a distinct product vector rather than a routine feature addition.

The recommended path is:

1. Preserve the work-centered character of the system.
2. Begin with chapter-level immutable review packages.
3. Use authenticated access and explicit author-controlled sharing.
4. Track simple, objective contribution metrics.
5. Avoid ratings, feeds, followers, payments, and virtual currencies.
6. Use existing NovelKMS summaries and Codex data to create optional reviewer context.
7. Validate the review economy with a small invitation-only cohort before committing to a broad public network.
8. Design moderation, copyright, and privacy controls before opening the queue publicly.

The central product proposition should remain straightforward:

> NovelKMS helps authors organize, understand, revise, and improve their novels—with assistance from both AI and other human writers.

## 32. Implementation Status

Phase 1 ships in six slices. V38 (whole Phase 1 schema) landed with 1A.

| Slice | Scope                                                | Status                                             |
| ----- | ---------------------------------------------------- | -------------------------------------------------- |
| 1A    | Profiles & handles                                   | **Done** — V38, `review_profile`, `/app/community` |
| 1B    | Publish chapter → request + snapshot; My Requests    | **Done** — V39; backend + frontend shipped |
| 1C    | Queue, package view, snapshot reader                 | **Done** — backend + frontend; reviewer read path |
| 1D    | Write/submit review; Reviews Received; notifications | Next |
| 1E    | Contribution metrics                                 |                                                    |
| 1F    | Blocking, reporting, admin removal                   |                                                    |

**Slice 1C (reviewer read path).** `ReviewAccessService` is the first cross-user
read seam — the tenant filter's `default -> true` lets `/review/...` through, so
authorization is explicit here, returning 404 (never 403) for anything a viewer may
not see; the author always reads their own in any status. Endpoints `GET /review/queue`
(genre / min-max words / sort newest|oldest|fewest, offset paging), `GET /review/packages/{id}`,
`GET /review/packages/{id}/snapshot`. `ReviewQueueDao` applies every exclusion in SQL:
OPEN+PUBLIC, not-own, author ACTIVE, not past `closes_at`, below `max_reviews` cap,
no block either direction. `max_reviews` is now enforced (was advisory through 1B).
Block-awareness is wired read-only (`UserBlockDao`) ahead of 1F. Reviewer-facing DTOs
expose `authorHandle` only — never `authorUserId` or `sourceEntityId`; the package view
carries no `contentHtml`. Frontend Review Queue tab at `/app/community?tab=queue`;
the snapshot reader renders another user's HTML in a sandboxed iframe, the first
cross-user render boundary in the app.

Resolved open questions (§30.2): **1** chapter-only initially; **2** anonymity deferred to Phase 2;
**5** closed requests stay readable to participants indefinitely, drop out of the queue; **6** no;
withdraw-and-rewrite instead; **7** yes; **9** yes, visibility is a disclosure setting not a
contribution setting; **13** any authenticated user who can see the package; **15/16** terms-level
prohibition plus a self-disclosure flag (`human_review.ai_assisted`).

Still open: **3, 4** (reviewer copy/download — 1C ships a "don't redistribute" watermark
but no technical copy-block), **8, 10, 11, 12, 14**.

## Human Review Network — Slice 1D (write / submit / receive reviews) — DELIVERED

Completes the Phase 1 loop: a reviewer can now write, save, submit, and withdraw a
review of a package, and an author can read the feedback they receive.

**Migration:** V41 adds `human_review.author_read_at TIMESTAMP` (nullable; NULL =
unread). This single column is the whole of Phase 1's notification model — the
Reviews Received badge is `COUNT(submitted reviews of my requests WHERE
author_read_at IS NULL)`. No notification table, no email (deferred to 1F, when
close/withdraw/moderation events make an inbox worthwhile).

**Backend:**
- `HumanReview` model + `ReviewWritingSummary` / `ReviewReceived` DTOs.
- `HumanReviewDao` — DRAFT/SUBMITTED/WITHDRAWN machine, block-filtered writing &
  received list reads, `countSubmitted` (cap), `countUnreadForAuthor` (badge),
  ownership-guarded `markAuthorRead`.
- `HumanReviewService` — owns all `human_review` access; two gates: `ensureCanStart`
  (new review: OPEN+PUBLIC, not self, not blocked, author active) and `ensureCanWrite`
  (existing draft: OPEN or PAUSED). 404 for every cross-user denial; 403 only for the
  caller's own suspension.
- `HumanReviewResource` @Path("/review"): `GET|PUT /packages/{id}/review`,
  `POST .../review/submit`, `.../review/withdraw`, `GET /reviews/writing`,
  `GET /reviews/received`, `GET /reviews/received/unread`,
  `POST /reviews/received/{reviewId}/read`.

**Frontend:** review editor inside `ReviewPackageDialog` (plain-text body wrapped in
`<p>`, AI-assist self-disclosure, Save/Submit/Withdraw, Revise for submitted);
`MyWritingPanel` and `ReviewsReceivedPanel`; unread badge on the Reviews Received
tab. Received review bodies render as **plain text** (`htmlToPlain`) — a cross-user
render boundary that needs no iframe while reviews are plain-text only.

**Verification:** static Java check ✓, H2 V1→V41 replay ✓, live SQL smoke test ✓,
esbuild transform ✓. `mvn test` pending (Maven unavailable in the build env).

**Known gap (watchlist):** §30.2 Q5 participant-read of paused/closed packages not
wired — 1C's `ReviewAccessService.authorizeRead` still requires OPEN+PUBLIC. Reviewers
can withdraw on any request state but can't re-open the reader once a request leaves
OPEN. Fix = participant-aware read in `ReviewAccessService` (ripples its ctor + 2 tests);
scheduled as its own slice.