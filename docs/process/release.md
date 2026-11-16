# Release checklist (every alpha)

Every APK that goes to the phone follows these steps, in order. If a step doesn't apply, say so in the commit.

1. **Scope.** Each user request is listed in the commit message. Anything deferred is named in the reply.
2. **Engine change → engine test.** Each new rule in `engine/` gets a test in `engine/src/test`. A bug found in
   the field gets a test that reproduces it first.
3. **Processing change → version bump.** If walk processing changes (TrackProcessor, arrivals, map
   matching), bump `Repository.PROCESSING_VERSION` so stored walks are reprocessed.
4. **Schema change → migration.** A Room entity change needs a new DB version and a `Migration`. Never
   use destructive fallback. Data the player made (walks, decisions) must survive.
5. **New dependency, asset or host → intake.** Follow `docs/security.md` §3. For art, run
   `tools/asset_intake.py` and record the license in `CREDITS.md`. For a new host, add a row to §2 and
   to `tools/policy/hosts.txt`.
6. **Design objects → legend.** A new map symbol, texture or resource means updating `tools/gen_legend.py`, then
   running it and regenerating the list in `Legend.kt`.
7. **Docs.** Update `docs/design.md` (a section per feature, noting the alpha version). If a real choice was made,
   add a decision record in `docs/decisions/`.
8. **Version.** Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Use a minor bump for features
   and a patch bump for fixes.
9. **Gate.** Run `ANDROID_HOME=/opt/android-sdk tools/check.sh` and make sure it prints `All checks passed.` CI runs the same.
10. **Ship.** Commit (`Alpha X.Y.Z: summary` + bullets), push to the working branch, and send the APK.
11. **Report.** Tell the player what changed, what was **not** verified on the phone, and what to look for in the
    next diagnostics.

## Field feedback loop

- Diagnostics come first: read the log before guessing. Every new subsystem logs one summary line under
  its own tag, e.g. `[map]`, `[geo]`, `[process]`, `[economy]`.
- A fix for field data names the log line that proved the cause in its commit message.
