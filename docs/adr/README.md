# Architecture Decision Records

Short records of decisions that change the architecture or fill a gap in a spec
([WORKFLOW.md](../WORKFLOW.md) §4.2 and §5). One file per decision: `NNNN-short-title.md`, numbered in
order, never renumbered. A superseded ADR stays in place and links to its replacement.

`0001-toolchain.md` is reserved for the outcome of the M0 toolchain spike ([ROADMAP.md](../ROADMAP.md)).

## Template

```markdown
# NNNN. Title

- Status: proposed | accepted | superseded by NNNN
- Date: YYYY-MM-DD
- Owning doc updated: <doc and section>

## Context
What forced the decision. Link the spec sections or issues involved.

## Decision
The rule, stated so that a test or reviewer can check it.

## Consequences
What becomes easier, what becomes harder, what must be revisited and when.
```
