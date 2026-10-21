# Refactor

**Summary**  
Extract task status transitions out of TaskService

**Description**  
A detailed description of the current structure, why it is hard to work with, and what the code should look like afterwards.

**Motivation**  
Status rules are spread across several methods, which makes them hard to test and easy to break.

**Outcome**  
Transition rules live in one small, pure unit with no change to external behavior.

**In scope**  
- Move transition logic into a dedicated module
- Update callers
- Add or adjust tests that pin the current behavior

**Out of scope**  
- New features or behavior changes
- Database schema changes

**Risks**  
- Subtle behavior differences in edge-case transitions
- Merge conflicts with in-flight work on the same files

**Exit criteria**
- Existing tests pass without modification to their assertions
- Behavior is unchanged for API consumers
- Code is simpler, with no dead code left behind

---
**Steps**

- [ ] Step 1
- [ ] Step 2
- [ ] Step 3
