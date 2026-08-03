---

name: 🐛 Bug Report
about: Use this template to report system errors or unexpected behavior
title: “[BUG] “
labels: bug
assignees: “”

---


## 🚨 Bug Summary

<!-- Clearly describe the bug in one or two sentences. -->

* (Example) A 500 Internal Server Error occurs when calling the PVT result synchronization API in guest mode.

### 👣 Steps to Reproduce

<!-- Provide the exact steps required to reproduce the bug. -->

1. Open the application in guest mode.
2. Complete the PVT test.
3. Navigate to the result screen.

## 🎯 Expected Behavior

<!-- Describe what you expected to happen. -->

### Expected: 
The API should return a 200 OK response.

## 🖥️ Actual Behavior

<!-- Describe what actually happened. -->

### Actual: 
The API returned a 400 Bad Request response.

## 📱 Environment

<!-- Provide relevant context for troubleshooting. Leave unknown fields blank. -->

* User Status: (Apple Sign-In user / Guest user)
* Affected Screen or API: (Example: Home screen / POST /api/v1/evaluations)
* Device / OS: (Example: iPhone 13 / iOS 17)

🔍 Logs & Payload

<!-- Attach the request JSON sent by the iOS client, the backend response error code, relevant logs, or screenshots if available. -->
<details>
<summary>View Error Logs or Payload</summary>
// Paste the request/response JSON or error logs here.
</details>
