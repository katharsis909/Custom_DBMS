---
description: Feature impact review and edge case documentation rules
always_on: true
---

# Feature Review Rules

- Whenever implementing or modifying a feature, ALWAYS review if the change affects existing features.
- Verify that existing features continue working successfully and pass test suites.
- If a feature works only under specific cases or constraints, document those edge cases in `AI_AGENT_RULES.md` and `README.md`.
