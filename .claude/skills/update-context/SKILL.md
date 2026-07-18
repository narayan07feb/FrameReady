---
name: update-context
description: Use immediately after finishing any nontrivial feature, fix, or refactor in this repo — before reporting the task done. Refreshes CLAUDE.md (and README.md when the change is user-facing) so the next agent or session starts oriented instead of re-deriving context. Trigger phrases — "update the context file", "update readme", "sync docs", "wrap up this feature".
---

# Update Context

This repo's `CLAUDE.md` is the orientation doc for any agent picking up work here cold. It goes
stale the moment a feature lands and nobody updates it. This skill is the mandatory last step of
finishing a feature in this repo — run it before telling the user the task is done, not as a
separate favor they have to ask for.

## When to run this

- You just finished implementing, fixing, or substantially refactoring something in this repo.
- You're about to give the user a "done" / wrap-up summary.
- The user explicitly asks to "update context", "update the readme", "sync docs".

Skip it for: pure exploration/investigation with no code change, a one-line typo fix, or work the
user explicitly scoped as throwaway/experimental.

## Steps

1. **Diff what actually changed.**
   ```bash
   git status --short
   git diff --stat
   ```
   Read the actual diff for anything non-obvious — don't rely on memory of what you intended to do.

2. **Decide what's CLAUDE.md-worthy.** CLAUDE.md documents *current architecture and state* —
   module boundaries, source-set layout, API shape rules, cross-cutting conventions (theming,
   accessibility patterns, build/verify commands), and known non-obvious gotchas. It is NOT a
   changelog — don't append a dated bullet log. Instead, edit the relevant section in place so it
   describes the *current* truth. If the change invalidates something CLAUDE.md currently claims
   (a module that no longer exists, an API shape that changed, a convention that's been replaced),
   fix that section — don't leave the old and new both described.

3. **Decide what's README.md-worthy.** README.md is the public-facing pitch for library consumers:
   installation, public API usage, benchmark numbers, feature descriptions. Update it only when the
   change affects what a *consumer* of the library sees or does — a new public API, a changed
   integration step, updated benchmark results. Internal refactors (e.g. how `frameready/`'s
   source sets are organized, an internal-only class rename) do NOT belong in README.md.

4. **Edit both files directly** (Edit tool, not a full rewrite) — keep changes surgical, matching
   the existing structure/tone of each file. If a genuinely new section is needed (e.g. a new
   module was added), add it following the pattern of the closest existing section.

5. **Verify you didn't break either file's own claims**: if CLAUDE.md gives a build/verify command,
   the command should still be accurate for the current module layout. If README gives a code
   sample, the API in the sample should match what actually compiles now.

6. Mention in your wrap-up to the user, briefly, that CLAUDE.md/README were updated — one line, not
   a separate report.

## What NOT to do

- Don't create new standalone docs (`ARCHITECTURE.md`, `CHANGELOG.md`, etc.) unless the user asks —
  CLAUDE.md and README.md are the two canonical files.
- Don't turn CLAUDE.md into a running log of every session — it describes the present, not the
  history (that's what `git log` is for).
- Don't touch README.md for internal-only changes nobody outside this repo would notice.
