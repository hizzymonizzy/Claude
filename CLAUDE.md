# This repository holds separate projects

Each project lives in its own top-level folder with its own `CLAUDE.md`, docs, build and rules. Work inside
exactly one project per task, and read that project's `CLAUDE.md` before doing anything else.
Rules, tools, agents and decisions from one project never apply to another.

| Folder | Project | Notes |
|---|---|---|
| `wander-world/` | Wander World: Android walking/paddling app that grows a world from GPS | Its own `CLAUDE.md`. Agents: `wander-world-*` |

- Starting a new project: create a new top-level folder with its own `CLAUDE.md`, add a row above, and prefix
  any agent in `.claude/agents/` with the project's folder name.
- CI: one workflow per project in `.github/workflows/`, each limited to its own folder with `paths:` filters.
- If it isn't clear which project a request is about, ask.
