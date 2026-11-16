# 0002 Official Overpass server only

**Context.** `overpass-api.de` sometimes returns 502 or 429. Community mirrors are faster but are run by unknown
operators who would see where the player walks.

**Decision.** Use only `overpass-api.de`. Handle failures with per-tile backoff and by building the world
without network data. Keep queries small: sea features are asked for as ids only (alpha 0.9.1), because their
full geometry timed out the server. Relations are fetched as bounding boxes first, and only those under 6 km
across are fetched whole (alpha 0.9.2).

**Consequences.** Place data can arrive late. The diagnostics log shows every failed request.
