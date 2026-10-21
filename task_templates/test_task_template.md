# Test

**Summary**  
Add integration tests for the task creation endpoint

**Description**  
A detailed description of the testing gap, including the risk it leaves uncovered and any recent incidents it relates to.

**Outcome**  
The task creation flow is covered by reliable automated tests that fail when the behavior regresses.

**Test scope**  
- Happy path with a valid payload
- Validation errors for missing and malformed fields
- Authorization failures
- Duplicate and concurrent requests

**Out of scope**  
- Performance and load testing
- Frontend component tests

**Test type**  
Integration (unit / integration / end-to-end / performance)

**Environment and test data**  
Local Postgres test database, seeded organization and project fixtures.

**Exit criteria**
- New tests pass locally and in CI
- Tests are deterministic over repeated runs
- Tests fail when the covered behavior is broken
- No unrelated production code changes

---
**Steps**

- [ ] Step 1
- [ ] Step 2
- [ ] Step 3
