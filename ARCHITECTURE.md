# RDM Online: Proposal, Permission, and Event Flows

This document describes the currently implemented persistence and service
boundaries. It is intentionally narrower than a product specification: some
policy decisions, such as majority calculation and regular-session scheduling,
remain service-layer work to be added.

## Design principles

- **SQL is authoritative.** Immutable facts and append-only events live in the
  database.
- **Current state is a projection.** `proposal_current_state` and
  `permission_current_state` make authorization and status queries fast without
  replaying an entire event history.
- **Invalidation is not deletion.** Repeals, role loss, and dependent
  permission changes append invalidation events and update projections.
- **SSE is downstream.** Durable outbox rows are created during domain
  transactions and can be published after commit to the in-memory SSE service.

## Proposal flow

1. `ProposalService.createProposal` inserts immutable proposal facts and a
   `CREATED` event.
2. A `ProposalSession` defines the voting context. Its `noAbstain` flag is
   intended for re-election sessions.
3. `recordVote` inserts one immutable vote per voter, proposal, and session.
   Abstention is rejected when the session has `noAbstain = true`.
4. Proposal events update `proposal_current_state`, and a
   `PROPOSAL_STATE_CHANGED` record is added to the outbox.
5. `resubmitWithoutChanges` creates a new proposal ID with the same content
   hash and links it to the original through `origin_proposal_id`. The supplied
   session ID preserves the conceptual session relationship.
6. `repeal` appends `REPEALED` and invalidates permissions granted by that
   proposal.

Proposal text is never updated in place. A changed proposal should be a new
record with a new ID and an origin reference.

## Veto flow

Veto permissions are scoped by `legal_area` and may optionally carry a role
name. `ProposalService.canVeto` requires:

- an active veto permission;
- matching proposal legal area;
- no veto of the actor's own role removal;
- no veto by a role against mutations to that role's own rules.

`veto` records the veto in `proposal_veto_events` and appends a proposal
`VETOED` event. Veto permissions can themselves be granted by a proposal.

## Permission and role-removal flow

Permissions are immutable grants. A grant is either:

- proposal-originated (`grantFromProposal`), or
- role-originated (`grantFromRole`).

Each grant has append-only permission events and a current validity projection.
When a proposal is repealed, or a grantor loses the role under which a
permission was granted, `PermissionService` invalidates the grant and walks
`permission_dependencies` to invalidate descendants.

`RoleRemovalConditions` records which proposal can remove a particular user's
role. `removeRoleForProposal` deactivates the assignment, consumes the
condition, and invalidates permissions granted under that role.

## Durable outbox and SSE

`OutboxService` writes a durable event handoff record. `OutboxPublisher` reads
unpublished rows, invokes an injected delivery callback, and marks successful
delivery. The callback can translate records into the existing typed
`EventService` events.

The SSE endpoint is `/api/events`. It requires a session and accepts repeated
`scope` parameters, for example:

```text
/api/events?scope=fundable:12&scope=awards
```

Subscriptions use bounded queues. If a queue overflows, the event service sends
`Resync` and closes the stream so the client can refetch coherent SQL state.

## Fundable history

Fundable mutations refresh the derived cache and append immutable rows to
`fundable_edits`:

- `CREATED`
- `DELETED`
- `COMPONENT_ADDED`
- `COMPONENT_REMOVED`

The edit record is deliberately not a foreign key with cascading deletion, so
history survives removal of the current fundable row. Existing fundable reads
continue to use `FundableDerived`; the history table is ready for a history
endpoint and frontend timeline.

## HTTP API currently documented in route code

The route KDoc describes the current endpoints and is intended to be consumed
by Ktor's OpenAPI route inference:

- `/api/auth/*`: Discord login callback, session profile, logout, admin status.
- `/api/rest/fundable/*`: cached fundable/component reads and authenticated
  mutations.
- `/api/awards/*`: award categories and suggestions.
- `/api/admin/*`: administrator-only award administration.
- `/api/events`: scoped authenticated SSE.

Proposal and permission services currently exist as service/database APIs; no
proposal REST resource has been exposed yet. Majority finalization, emergency
confirmation at the next regular session, and a continuously running outbox
worker are also intentionally not implied by the current routes.
