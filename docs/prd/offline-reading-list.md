# Offline Reading List — Product Requirements Document

Status: Draft for implementation  
Date: 2026-10-10  
Platform: Android, within the existing Lua Playground application

## Product summary

A personal reading queue that lets users save article URLs, fetch titles over HTTP, organize entries with tags, and mark articles as read. Saved entries and personal notes remain available without a connection and survive app restarts through local JSON storage.

For the MVP, “offline” means access to the saved list, metadata, and notes. Reading the original article requires a connection and opens in the browser. Full article downloads are a future feature and must not be implied by UI wording.

## Problem and audience

People who collect articles across browsing sessions need a small, reliable place to remember what to read next. The primary user is an individual maintaining a reading queue on one Android device, without an account or cloud service.

## Goals

- Save an article even when the network is unavailable.
- Make unread articles easy to find through status and tag filters.
- Preserve saved entries, tags, notes, and read status across restarts.
- Demonstrate Lua-owned screen behavior using explicit native HTTP, storage, and browser capabilities.

## MVP scope

| Capability | Required behavior |
| --- | --- |
| Add article | Paste an HTTPS URL, optionally enter a title, tags, and a note, then save locally. |
| Fetch metadata | After saving, request the page title. Use the supplied title or URL as the fallback. |
| List articles | Show title, hostname, tags, read status, and metadata fetch state; newest first. |
| Filter | Select All, Unread, or Read and optionally one tag. Both filters apply together. |
| Article details | Show saved fields, edit title/tags/note, toggle read status, retry metadata, or open the original URL. |
| Delete | Require confirmation identifying the article before removing it. |
| Offline use | Support viewing, adding, editing, filtering, toggling read status, and deleting saved entries. |

Not included: accounts, sync, sharing integration, full article extraction, offline images, search, folders, notifications, background refresh, analytics, or remote Lua delivery.

## Primary flows

### Save an article

1. User opens the reading list and selects Add article.
2. User enters a URL and optional title, tags, and note.
3. App validates the input and checks for duplicates.
4. App persists the entry before reporting success or starting metadata retrieval.
5. Entry appears as unread. A successful title fetch updates the saved entry; a failure leaves it usable with a retry action.

### Read and organize

1. User filters the list by status and/or tag.
2. User opens an entry to view or edit its saved details.
3. Open original launches the browser through a native adapter. It does not automatically mark the entry as read.
4. User explicitly marks the entry read or unread. The change is confirmed only after persistence succeeds.

### Return offline

1. App loads the last successfully saved document without making a network request.
2. User can manage all saved entries locally.
3. Metadata retrieval or opening the original may fail; the app preserves local data and explains the failure.

## Functional requirements

### Input and duplicate handling

- Accept absolute HTTPS URLs on port 443 without embedded credentials. Trim surrounding whitespace.
- Remove fragments for storage and duplicate comparison; normalize hostname case and the default port. Preserve path and query semantics, including trailing slashes.
- A duplicate opens the existing entry and displays “Already saved”; it must not overwrite notes, tags, title, or read status.
- Proposed product limits: URL 2,048 characters, title 300 characters, note 4,000 characters, and five tags of at most 30 characters each.
- Trim tags, discard empty tags, and deduplicate case-insensitively while preserving the first entered spelling. Tag filtering also compares case-insensitively.
- Reject invalid or oversized fields with an actionable inline error; preserve the form contents.

### Metadata retrieval

- Retrieve only the page title; no images, article body, JavaScript, or linked resources.
- Use a narrow native title-fetch capability. Lua receives structured title/error results rather than parsing arbitrary HTML.
- Support a bounded HTML title parser that decodes entities and collapses whitespace. Missing, malformed, unsupported, or oversized responses retain the fallback title.
- Preserve user-entered titles. An automatic title update may replace only an unchanged automatic fallback; it must not overwrite an edit made while the request was running.
- Fetch after a new save or explicit Retry; do not refresh automatically on startup or retry in a loop.
- Show pending, available, and failed states. Pending state from an interrupted session becomes retryable on the next launch.
- Ignore late results for deleted entries and obsolete requests. Persistence failure after retrieval keeps the previous saved state and offers retry.

### Persistence and recovery

- Use one versioned JSON document through the existing `JsonStore` boundary. Respect its current 256 KiB total document limit.
- Store only entries and necessary metadata; filter selection and form drafts can remain session state.
- Apply each mutation only after its write succeeds. Failed writes leave the previous visible and persisted list intact.
- Quota errors explain that the user must shorten content or delete entries; never silently discard data.
- Missing storage means an empty list. Corrupt data, unsupported schema versions, or unavailable storage show a recoverable error and must not trigger an automatic overwrite or reset.
- Do not silently skip invalid records. Preserve the original document for a later recovery attempt.

## Data model

The root document contains `schemaVersion: 1` and an `articles` array.

| Article field | Purpose |
| --- | --- |
| `id` | Stable local identifier, independent of list position. |
| `url` | Validated, normalized article URL. |
| `title` | User title, fetched title, or URL fallback. |
| `titleSource` | `user`, `metadata`, or `fallback`; prevents automatic overwrites. |
| `tags` | Ordered, unique tag strings. |
| `note` | Optional user text, stored as an empty string when absent. |
| `isRead` | Boolean; defaults to false. |
| `createdAt` | UTC creation timestamp used for newest-first ordering. |
| `metadataState` | `pending`, `available`, or `failed`. |

Use a deterministic ID tie-breaker when creation timestamps match. Do not persist raw HTTP responses or exception messages.

## Screens and accessibility

- Reading list: Add action, status controls, tag filter, list entries, and distinct empty-list/filter-empty/error states.
- Add form: labeled fields, validation feedback, Save and Cancel actions; prevent duplicate submissions while saving.
- Detail screen: saved information, edit controls, read toggle, Retry when needed, Open original, and Delete.
- Delete confirmation: article title, Cancel, and Delete.
- Render every screen and dialog with Jetpack Compose through existing Lua UI primitives where they fit. Any missing primitive must have a concrete requirement here before extending the renderer.
- Give controls accessible labels, maintain readable contrast and text scaling, and communicate read/fetch states with text rather than color alone.
- Display “Saved list and notes available offline” rather than “Article downloaded.”

## Integration and security constraints

- Bundle the reading-list Lua script with the application and register it in `lua/app.lua`; keep each script under 500 KB.
- Reuse the todo persistence/event patterns, dynamic container, route registry, and JSON storage implementation.
- The current HTTP policy only grants the contributors script access to a fixed GitHub endpoint. Reading-list access requires an explicit native capability and policy; the existing permission must not be broadened globally.
- Before any page fetch, reject loopback, private, link-local, and reserved destinations, including unsafe resolved addresses. Validate the actual connection destination to prevent DNS rebinding. Reject credentials and routing-header overrides.
- Keep redirects disabled for the MVP. Explain redirect failures and allow the user to save a final URL manually.
- Apply existing HTTP timeout, response-size, concurrency, and cancellation limits. Cancel work when its owning session closes.
- Browser launching uses a narrow native adapter that accepts only validated saved URLs. Lua receives no unrestricted Android, filesystem, or networking access.
- Requests are unauthenticated and send no cookies or saved notes/tags. Explain that metadata fetching contacts the article's website.
- If safe arbitrary-host fetching cannot fit the existing boundary, retain manual saving and treat metadata fetching as incomplete until the native policy is implemented and tested.

## Acceptance criteria and unit tests

Every behavior above must have fast, in-memory JUnit coverage using fake storage, network/title results, browser actions, clock, and identifiers as needed. No live websites or emulators are required for unit tests.

| Scenario | Acceptance criterion |
| --- | --- |
| Save offline and restart | Entry and every user field reload unchanged. |
| Invalid URL or field limit | No write or request occurs; entered data remains editable. |
| Equivalent or distinct URLs | Defined normalization identifies duplicates without collapsing meaningful path/query differences. |
| Duplicate save | Existing entry opens with no mutation or duplicate request. |
| Metadata outcomes | Success, timeout, HTTP failure, redirect, missing title, malformed HTML, and oversized response leave valid local entries. |
| Fetch races | User title edits, deletion, repeated retries, and session closure cannot cause stale updates. |
| Read toggle and filters | All combinations of status/tag filters return the expected entries; toggles persist. |
| Tags and ordering | Case variants, blanks, timestamp ties, and empty filter results follow the defined rules. |
| Storage failure | Add, edit, toggle, delete, and metadata writes preserve previous state when persistence fails. |
| Quota and damaged data | Boundary-size documents work; oversized writes and corrupt/unknown documents fail without data loss. |
| Delete | Cancel keeps the entry; confirmation removes only the selected entry and survives restart. |
| Browser action | Only an explicit Open original event invokes the adapter; launch failure does not change read status. |
| Native safety | URL validation, forbidden destinations, DNS changes, redirect rejection, and response bounds fail before unsafe access. |
| UI contract | Rendered trees expose required labels, actions, states, and offline wording. |

## Delivery and completion

1. Implement the local list, forms, details, filters, and persistence with their unit tests.
2. Implement the restricted title-fetch and browser adapters, asynchronous error handling, and boundary tests.
3. Register the route and finish end-to-end Lua session tests with in-memory fakes.

The feature is complete when all acceptance criteria are covered, `./gradlew test` passes, and the Android debug build succeeds. A manual Compose accessibility and device usability check complements the unit tests before release.

Primary success measure: a user can save an article, restart offline, find it by tag, and change its read status without losing data. Secondary measure: metadata failures never block saving or managing the list. No telemetry infrastructure is required for the MVP.
