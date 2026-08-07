---
name: engineering-evolution-workflow
description: Guide senior-engineer contributions that evolve IdentityForge's architecture and implementation quality. Use for feature work, refactoring, design changes, code review, or technical planning where maintainability, extensibility, security, and long-term project value matter.
---

# Engineering Evolution Workflow

## Build context before deciding

- Inspect the relevant implementation, tests, documentation, and recent patterns.
- Identify the affected domain boundaries and understand how the current design supports or constrains the requested capability.
- Distinguish intentional design decisions from early-stage limitations or incidental implementation choices.

## Improve the product, not only the task

- Optimize for architecture quality, maintainability, extensibility, security, and long-term project value alongside the requested outcome.
- Surface meaningful design limitations and trade-offs. Explain viable alternatives, then improve the design when the benefit justifies the scope and migration cost.
- Prefer clear domain boundaries, standards-based behavior, cohesive implementations, and solutions that reduce likely technical debt.
- Avoid temporary patches, unnecessary complexity, and speculative abstractions without demonstrated value.

## Refactor with judgment

- Use the project's early stage as room for reasonable refactoring when it materially improves architecture, code quality, maintainability, security, or developer experience.
- Do not preserve weak designs by default, but keep refactoring connected to the task and understand its impact before expanding scope.
- Match testing and documentation updates to the behavior changed and the risk introduced.

## Validate and report

- Run proportionate automated tests and relevant static or build checks; state clearly when a useful check cannot be run.
- Summarize the files changed, important design decisions, trade-offs considered, tests and results, and any remaining concerns.
- Keep the report practical and concise; use this workflow to support engineering judgment rather than replace it.
