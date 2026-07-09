# Bounded Context: Membership

- **Status:** Iteration 1 — in progress
- **Purpose:** Own the identity and wellness profile of a member: who they are, and what they want from
  their wellness journey.

This document is the **living specification** for the Membership context. It is the source of truth;
code and the executable Gherkin specs must agree with it.

---

## 1. Ubiquitous language

| Term                | Meaning                                                                                          |
|---------------------|-------------------------------------------------------------------------------------------------|
| **Member**          | A person with a WellConverge account. The context's aggregate root.                             |
| **Registration**    | The act of creating a Member from an email + name. Produces a `MemberRegistered` event.         |
| **Onboarding**      | The one-time act of a registered Member declaring their wellness profile (goals, DOB).          |
| **Wellness goal**   | An aspiration the Member selects (e.g. Sleep Better, Move More, Eat Well, Stress Less).          |
| **Member status**   | Lifecycle state of a Member: `REGISTERED` → `ONBOARDED`.                                         |
| **Email address**   | The unique login/identity handle. No two Members share one.                                      |

> **Naming rule:** every term above appears verbatim in the code (class, method, or field names).

## 2. Scope

**In scope (Iteration 1)**
- Register a new member (email + full name).
- Complete onboarding (choose ≥1 wellness goal, optional date of birth).
- Fetch a member by id.

**Out of scope (later iterations)**
- Authentication / password / verification email (Membership will later publish to an Auth concern).
- Editing profile after onboarding, deactivation, GDPR delete.
- Avatars, addresses, preferences.

## 3. Domain model

### Aggregate: `Member` (root)

| Field           | Type                    | Notes                                                        |
|-----------------|-------------------------|--------------------------------------------------------------|
| `id`            | `MemberId` (UUID)       | Identity, assigned at registration.                          |
| `email`         | `EmailAddress`          | Value object; unique across members (enforced by repository).|
| `fullName`      | `FullName`              | Value object.                                                |
| `status`        | `MemberStatus`          | `REGISTERED` on creation, `ONBOARDED` after onboarding.      |
| `profile`       | `WellnessProfile?`      | Null until onboarding completes.                             |
| `registeredAt`  | `Instant`               | Set at registration.                                         |
| `onboardedAt`   | `Instant?`              | Set at onboarding.                                           |

### Value objects

- **`MemberId`** — wraps a non-null `UUID`. Factory `MemberId.newId()` and `MemberId.of(uuid)`.
- **`EmailAddress`** — non-blank, matches a basic email shape, normalized to lowercase/trimmed.
- **`FullName`** — non-blank, trimmed, ≤ 120 chars.
- **`WellnessProfile`** — a non-empty set of `WellnessGoal` + optional `dateOfBirth` (must be in the
  past if present).
- **`WellnessGoal`** — enum: `SLEEP_BETTER`, `MOVE_MORE`, `EAT_WELL`, `STRESS_LESS`, `BUILD_STRENGTH`.

### Invariants (the rules the aggregate must always keep)

- **INV-1** A Member always has a valid email and full name.
- **INV-2** Email is unique across all Members. *(Enforced at the application/repository boundary, since
  uniqueness spans the whole set — the aggregate alone can't see other members.)*
- **INV-3** Onboarding requires at least one wellness goal.
- **INV-4** A Member can complete onboarding **only once**; onboarding an already-`ONBOARDED` member is
  rejected.
- **INV-5** `dateOfBirth`, if supplied, is in the past.

### Domain events

- **`MemberRegistered`** { memberId, email, registeredAt }
- **`MemberOnboarded`** { memberId, goals, onboardedAt }

*(Events are raised by the aggregate. In Iteration 1 they are recorded but not yet published to other
contexts — a later iteration adds an event bus.)*

## 4. Use cases (application ports)

| Use case (`ports.in`)      | Command                                             | Outcome / errors                                    |
|----------------------------|-----------------------------------------------------|-----------------------------------------------------|
| `RegisterMemberUseCase`    | `RegisterMemberCommand(email, fullName)`            | Returns `MemberId`. Error: `EmailAlreadyRegisteredException` (INV-2), validation errors (INV-1). |
| `CompleteOnboardingUseCase`| `CompleteOnboardingCommand(memberId, goals, dob?)`  | Marks member `ONBOARDED`. Errors: `MemberNotFoundException`, `AlreadyOnboardedException` (INV-4), validation (INV-3, INV-5). |
| `GetMemberUseCase`         | `memberId`                                          | Returns a `MemberView` or `MemberNotFoundException`. |

**Outbound port:** `MemberRepository` — `save(Member)`, `findById(MemberId)`, `findByEmail(EmailAddress)`.

## 5. Acceptance criteria (executable BDD)

Written as Gherkin and executed by Cucumber; see
[`backend/membership/membership-application/src/test/resources/features/`](../../../backend/membership/membership-application/src/test/resources/features/).

### Registration
- ✅ A new member registers with valid details → they exist with status `REGISTERED`.
- ✅ Registering with an email already in use is rejected.
- ✅ Registering with a malformed email is rejected.

### Onboarding
- ✅ A registered member completes onboarding with one or more goals → status becomes `ONBOARDED`.
- ✅ Completing onboarding without any goal is rejected.
- ✅ Completing onboarding a second time is rejected.

## 6. REST surface (adapter — inbound)

| Method | Path                          | Body                                            | Success        | Errors                       |
|--------|-------------------------------|-------------------------------------------------|----------------|------------------------------|
| POST   | `/api/members`                | `{ email, fullName }`                            | `201` + `{id}` | `409` email taken, `400`     |
| POST   | `/api/members/{id}/onboarding`| `{ goals: [...], dateOfBirth? }`                | `200` member   | `404`, `409` already, `400`  |
| GET    | `/api/members/{id}`           | —                                               | `200` member   | `404`                        |

## 7. Open questions / future

- Where does authentication live — inside Membership or a separate `Identity/Access` context?
- Email verification flow before a member becomes fully active.
- Should `WellnessGoal` be a static enum or a configurable catalog owned by Content?
