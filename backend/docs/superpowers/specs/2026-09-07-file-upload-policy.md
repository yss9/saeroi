# File Upload Policy

## Goal

Fix the rules every part must follow when it stores a user-supplied file, so that the storage
interface behind them can be written once and swapped later. Material evidence, processing
evidence, product images, and dispute attachments all upload files; without one policy each part
picks its own limits and the same photo is accepted in one screen and rejected in the next.

Jira: `S15P21E104-245`. Implemented by `S15P21E104-246` (`FileStorageService`).

## Scope

This document fixes five things the ticket asks for — allowed extensions, maximum file size,
file count, storage path, and file naming — plus the two that fall out of them: hashing and
failure behaviour.

It does not choose the storage backend. `-246` provides the interface and a local filesystem
implementation; MinIO/S3 arrives in `-162`/`-248`. It also does not define the upload endpoint
(`-249`) or per-domain limits beyond the shared floor.

## Chosen Approaches

### Allowed extensions are a whitelist, checked against content

| Purpose | Allowed |
| --- | --- |
| Evidence photos, product images | `jpg`, `jpeg`, `png`, `webp` |
| Evidence documents | `pdf` |

A blacklist is the wrong shape here: the set of dangerous extensions is open-ended, and anything
we forget becomes an upload. The whitelist is short because the product only needs photographs
and weight slips.

**The extension alone is not the check.** A file named `photo.jpg` can be anything, so the
declared media type and the extension must agree, and both must be in the table above. Domains
that need something else request an addition rather than working around it — a per-domain
exception is how a whitelist quietly becomes a blacklist.

`svg` is deliberately absent. It is an image to a user and a script container to a browser;
serving one from our own origin would hand an attacker a stored-XSS vector.

### Maximum size is 10 MB per file, and it is enforced twice

`client_max_body_size 20m` is already set on nginx, so a larger request never reaches the
application. The application limit is lower on purpose:

- nginx `20m` bounds the whole multipart request, including several files and form fields
- the per-file limit is `10 MB`

Phone photographs are 3–8 MB, so 10 MB accepts the real input without inviting 50 MB uploads.
A PDF weight slip is far smaller.

Enforcing at both layers is not redundant. nginx rejects before the bytes are buffered, which is
what protects the server; the application check is what produces an error the user can read
(`413` from nginx is a bare page). If only nginx enforced it, every part would surface a
different failure.

### At most 10 files per request

Evidence for one material is a handful of photographs. Ten leaves room without letting a single
request occupy the disk. Domains needing more upload in several requests, which also gives the
user visible progress.

### Storage path is derived, never client-supplied

```
{reference_type}/{reference_id}/{yyyy}/{MM}/{uuid}.{ext}
                                 ↑ upload date, not business date
```

Example: `MATERIAL/1024/2026/09/9f2c1e08-....jpg`

- **`reference_type` first** so one prefix scan lists everything for a domain — that is how a
  retention or cleanup job will find files
- **`reference_id` second** so deleting one material's evidence is a prefix delete
- **year/month** keeps directory fan-out bounded on a filesystem backend; on S3 it is a no-op
- **the client never influences the path.** A supplied name containing `..` or a leading `/`
  escapes the directory, and that check is easy to write incorrectly. Deriving the whole path
  makes traversal impossible rather than defended.

### Stored name is a UUID; the original name is metadata

The stored object name is a fresh UUID plus the validated extension. The original filename is
kept as a column so a user can still recognise the file.

Two reasons. Original names collide (`IMG_0001.jpg` from two phones) and overwriting evidence
silently is unacceptable when the hash is recorded on chain. And original names carry characters
the filesystem, HTTP headers, and S3 keys each escape differently — Korean, spaces, quotes,
newlines — so any of them can become a bug.

### SHA-256 over the stored bytes, computed while streaming

`evidence_file.file_hash` is `CHAR(66)` — `0x` plus 64 hex characters, matching what
`MaterialToken.verifyEvidence` compares on chain. The hash is computed as the file streams to
storage, not by reading it back, so it cannot disagree with what was written.

The hash covers the bytes only. It is not a signature and proves nothing about who uploaded the
file; that is what the wallet signature over the evidence manifest is for.

### A failed upload leaves nothing behind

Storage writes happen before the DB row, so a crash between them would strand a file with no
record. Upload therefore writes to a temporary key and moves it into place only after validation
succeeds, and the caller deletes the object if its own transaction rolls back.

The reverse order — DB row first — is worse: a row pointing at a file that does not exist breaks
every screen that renders it, while an orphaned object only costs disk and is findable by prefix.

## Error Contract

| Condition | Status | Code |
| --- | --- | --- |
| Extension or media type not allowed | 400 | `FILE_TYPE_NOT_ALLOWED` |
| Extension and media type disagree | 400 | `FILE_TYPE_MISMATCH` |
| File exceeds 10 MB | 400 | `FILE_TOO_LARGE` |
| More than 10 files | 400 | `FILE_COUNT_EXCEEDED` |
| Empty file | 400 | `FILE_EMPTY` |
| Storage write failed | 500 | `FILE_STORAGE_FAILED` |

`FILE_TYPE_MISMATCH` is separate from `FILE_TYPE_NOT_ALLOWED` because the two need different
messages: one means "we do not accept this kind of file", the other means "this file is not what
its name claims", and a user who renamed a `.heic` to `.jpg` needs to be told which.

## Verification

`-246` covers the interface and the local implementation: a derived path never contains a
client-supplied segment, `..` in an original name cannot escape the root, the same original name
uploaded twice produces two objects, the returned hash matches an independently computed
SHA-256, and a write failure leaves no object.

Size and count limits are verified at the endpoint (`-249`) since they apply to a request rather
than a single file.
