# Feature template

**Summary**  
Upgrade Node runtime to 22.x on staging

**Description**  
A detailed description of the task, including the context, requirements and constraints needed to complete it.

**Outcome**  
Staging environment runs Node 22 with no test regressions.

**In scope**  
- Update the Dockerfile
- Refresh the lockfile
- Run the full test suite
- Update the CI base image

**Out of scope**  
- Production rollout (tracked separately)

**Dependencies**  
- SRE approval for the staging window
- Lockfile audit completed
- CI base image published

**Exit criteria**
- All tests pass
- CI green for 24 hours
- Sign-off from SRE

---
**Steps**

- [ ] Step 1
- [ ] Step 2
- [ ] Step 3
