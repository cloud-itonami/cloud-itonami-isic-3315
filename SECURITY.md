# Security Policy

This project handles transport-equipment-repair-shop coordination data
(asset/work-order records, safety-concern flags, return-to-service
coordination proposals). Treat vulnerabilities as potentially high
impact even when the demo data is synthetic -- a bypass of the Repair
Governor's hard checks could allow a repair-operation schedule or a
return-to-service coordination proposal to be proposed against an
unverified asset/work-order record or with an unresolved safety concern
still on file.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real asset/work-order, customer or personal data exposure
- authorization bypass
- Repair Governor bypass (including any path that lets a proposal
  commit with an `:effect` other than `:propose`, or that lets a
  repair-equipment-control / airworthiness-certification-decision /
  seaworthiness-certification-decision / rail-safety-certification-
  decision / return-to-service-sign-off marker through)
- audit-ledger tampering
- over-disclosure in safety-concern notices or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on asset/work-order data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Keep real asset/work-order/customer/personal data outside this
  repository.
- Run the full test suite (`clojure -M:dev:test`) before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never deploy a fork that has relaxed the Repair Governor's closed
  op-allowlist or forbidden-action-class check.
- If a real notification transport is ever added, keep its credentials
  (API keys, tokens) outside Git and implement it behind
  `transport-equipment-repair.notify/Notifier` via a portable (cljs/nbb)
  HTTP client -- do not add JVM-only interop to `src/`.
