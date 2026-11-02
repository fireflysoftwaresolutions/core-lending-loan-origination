# Initial loan contract basis

The contract-basis reader supplies verified Loan facts to the Domain initial-contract operation. It reads the current primary applicant and the original managed draft-intake terms from Core Loan through Spring Data. It does not create a contract, approve a loan, sign a document, or disburse funds.

Experience calls the generated Domain SDK. Domain calls the generated Core Loan SDK for this observation, then the generated Core Contract SDK for the separate contract action. Experience must not call this Core endpoint directly.

## Operation and response

`GET /api/v1/loan-application-contract-basis/applications/{applicationId}/actions/{actionId}`

The caller retains the action identifier for the initial-contract operation. The owner locates the original managed intake using the verified application, party, and resource tenant; the caller does not submit commercial terms or an asserted audit record. The response has `Cache-Control: no-store`.

| Result | Meaning |
| --- | --- |
| `200` | Current primary ownership and the original completed intake audit pair were verified. |
| `401` | Bearer authentication failed. |
| `403` | Current original actor, party, application, or tenant authority was refused. |
| `503` | Observation is unavailable or its completion cannot be safely returned. No contract command was dispatched by this operation. |

The response contains `applicationId`, `resourceTenantId`, `partyId`, `primaryApplicantId`, `simulationId`, `productId`, `requestedAmount`, `term`, `currencyCode`, and `intakeIntentDigest`. These are original intake terms with current ownership verification; they are not an approved offer or a contractual pricing calculation.

The official controller producer declares `observeInitialPrimaryLoanContractBasis`. Its generated SDK API is `ApplicationContractBasisControllerApi`; consumers must generate and compile the complete current owning specification through the repository's normal producer workflow. Do not hand-edit the specification or SDK Java, or infer a published compatible SDK from a source merge.

## Deployment prerequisites

The feature is opt-in. Set the following server-owned properties under `lending.contract-basis` through deployment configuration; obtain their values from the institution's approved identity and dataset binding rather than a request body.

| Property | Required value |
| --- | --- |
| `enabled` | `true` to construct the reader and its admission boundary. |
| `issuer` | Trusted issuer matching the verified original human principal. |
| `audience` | Exact Core Loan target audience. |
| `allowed-client-id` | Registered delegated caller accepted for this purpose. |
| `platform-tenant` | Exact identity-platform tenant. |
| `resource-tenant-id` | UUID of the managed Loan dataset. |
| `binding-id` | UUID of the explicit deployment binding. |
| `binding-version` | Positive binding version. |
| `binding-digest` | Lowercase 64-character hexadecimal definition digest. |
| `not-after` | ISO-8601 expiration instant for that binding. |

The application also needs its genuine `ReactiveJwtDecoder`, `fireflyJwtAuthenticationConverter`, exactly one current `PolicyEvaluationPort`, and the existing qualified Loan `connectionFactory`. The existing managed-intake and completed-audit deployment prerequisites must be installed. Do not grant application access as a schema owner, disable row security, or manufacture an audit record to satisfy a missing prerequisite.

A finite configured binding is not automatically renewed. Current policy is checked during the operation, including before delivery, and must retain its original decision provenance. Deployments must supply the appropriate trusted binding lifecycle; arbitrary caller tenant claims are not a replacement.

## Ownership, integrity, and recovery

The read requires the original authenticated intake actor and the current primary applicant. A different human does not gain access merely by presenting the same party identifier. This endpoint does not establish a general employee servicing permission.

The owner verifies one managed intake, one active `PRIMARY_APPLICANT` role, the retained primary association, and the actual simulation against the original intake digest. The immutable audit event must match the stored event identifier, resource stream, original actor, process/correlation identity, outcome, and record hash. The genuine framework completed-append accessor must confirm its canonical event/outbox pair.

Business creation time, audit event occurrence time, and audit record time are separate facts. The reader preserves them as originally stored. Their equality is not an integrity rule; changing an event timestamp while retaining its old record hash still fails verification.

The observation uses a bounded repeatable-read transaction. Technical audit scope installation does not create a new business intake or audit event. Missing, corrupted, or inaccessible original audit evidence makes the observation unavailable. A retry can only repeat the read under fresh current authority; it does not create a missing contract or authorize replay of an uncertain contract command.

## Qualification boundary

Owner source tests, native PostgreSQL verification, signed service startup, complete Experience → Domain → Core contract integration, and published SDK adoption are separate gates. The contract-basis reader is a prerequisite for the initial-contract journey. Approval, offer acceptance, signature, disbursement, repayment, and servicing require their own implemented owner operations and integration evidence.
