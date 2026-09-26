# Project Rules & Guidelines

## Command Execution & Tool Permissions
- **Git Operations**: Always execute all `git` commands (`git status`, `git add`, `git commit`, `git push`, `git checkout`, `git diff`, etc.) directly across all projects without requiring manual confirmation.
- Execute local git operations in the sandbox and network operations (`git push`, `git pull`, `git fetch`) unsandboxed.

## Feature Implementation & Impact Review
- **Feature Addition Review**: Whenever adding or modifying a feature, ALWAYS review whether the change affects other existing features.
- **Regression Verification**: Always verify that existing features continue working successfully without regressions.
- **Edge Cases & Special Constraints**: If a feature satisfies successful working only in specific cases or under known constraints, explicitly document these caveats in `AI_AGENT_RULES.md` and `README.md`.
