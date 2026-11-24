---
name: Feature request
about: Suggest an idea for Phone Agent
title: "[Feature] "
labels: enhancement
---

**Problem**
What can't the agent do today that it should? (A command it
misunderstands, a tool that's missing, a language it handles badly.)

**Proposed change**
What should Phone Agent do differently? Be concrete: a new tool in the
fixed toolbox, a parsing fix, a new spoken response.

**Scope note**
Phone Agent's ground rules (see CONTRIBUTING.md) are not negotiable:
the confirm gate is sacred — nothing irreversible happens without the
user approving the exact action; the LLM works through a fixed toolbox
of structured tool calls, never free-form actions; a spoken contact
name is never guessed when several match; and no wake word in v1. A
feature that breaks one of those is a regression, not an improvement.

**Alternatives considered**
Anything else you tried, or a simpler version of the same idea.
