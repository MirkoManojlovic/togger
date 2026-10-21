# Bug

**Summary**  
Login button does nothing on Safari 17

**Description**  
We identified a bug in the checkout flow that blocks paying customers from completing an order.

**Steps to reproduce**  
- Open the login page in Safari 17.
- Enter valid credentials.
- Click Sign in.

**Actual behavior**  
Nothing happens. No network request fires and no error is shown.

**Expected behavior**  
User is redirected to the dashboard within a second.

**Impact**  
All Safari users on the Pro plan, roughly 8 percent of daily sign-ins.

**Environment**  
macOS 14.4, Safari 17.4, build 2026.5.21

**Additional notes**  
Reproducible on every attempt. Workaround: reload the tab.
