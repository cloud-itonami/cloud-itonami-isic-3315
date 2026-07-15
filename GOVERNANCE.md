# Governance

`cloud-itonami-isic-3315` is an OSS open-business blueprint for
transport-equipment-repair-shop operations coordination -- coordination-
only, never repair-equipment control or an airworthiness/seaworthiness/
rail-safety-certification-authority decision.

## Maintainers

Maintainers may merge changes that preserve these invariants:
- this actor never holds repair-equipment-control authority.
- this actor never holds airworthiness/seaworthiness/rail-safety-
  certification-authority -- that remains the classification society's/
  airworthiness authority's/rail-safety authority's exclusively.
- every proposal this actor's advisor produces carries `:effect
  :propose`, and the Repair Governor remains independent of the advisor.
- hard policy violations (unknown op, non-`:propose` effect, forbidden
  action class, unverified asset, missing legal basis, unresolved safety
  concern) cannot be overridden by human approval.
- `:flag-safety-concern` always requires human sign-off, at every phase,
  unconditionally.
- a repair-order/asset record must be independently verified/registered
  before ANY of the four allowed ops -- broader than sibling
  `cloud-itonami-isic-3311`'s equivalent check, a genuine domain-design
  requirement for this ISIC class, never weakened without an explicit
  decision record (see below).
- every proposal, sign-off, log entry and notification path is
  auditable.
- sensitive operating and personal data stays outside Git.
- no JVM-only interop is added to `src/` (this build's cljs-first
  `.cljc` runtime-priority mandate) -- a real notification transport, if
  ever added, must go behind `transport-equipment-repair.notify/
  Notifier` via a portable (cljs/nbb) HTTP client, not `java.net.http`.

## Decision Records

Architecture decisions should be documented (an ADR or equivalent) when
changing the trust model, storage contract, closed op-allowlist, business
model, operator certification or license -- including any change to which
ops are auto-eligible at phase 3, or any change to the per-(jurisdiction,
asset-class) legal-basis catalog structure (see `transport-equipment-
repair.facts` ns docstring for why this catalog is keyed by asset class,
not just jurisdiction).

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, safety, audit and
data-flow review.

Certified operators can lose certification for:
- bypassing the Repair Governor's hard checks or the closed op-allowlist
- attempting to extend this actor's authority into repair-equipment
  control or an airworthiness/seaworthiness/rail-safety-certification-
  authority decision (including a return-to-service sign-off)
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
