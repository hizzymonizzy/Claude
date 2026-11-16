# 0001 Everything stays on the phone

**Context.** The app records where the player goes all day. This is the most sensitive data the player has.

**Decision.** Tracks, the world, health data and decisions live only in the app's private database, with backups
off. Only three things go out: map data requests for an area of about 3 × 3 km (Overpass), base-map tiles
(OpenFreeMap), and diagnostics the player chooses to share. No accounts, analytics, crash reporters or ad SDKs.
No personal details (name, email, home address) in any request.

**Consequences.** Nothing can be recovered if the phone is lost, until there's an encrypted export the player
controls. Every new host or library goes through `docs/security.md` §3, and `tools/check.sh` enforces this.
