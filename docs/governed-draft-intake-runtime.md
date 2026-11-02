# Governed DRAFT intake runtime deployment

Keep the intake feature disabled until the owner installs V18, V19 and the published audit protocol under its existing separate installer contract. V1–V18 remain byte unchanged by the primary-applicant extension. The application login must be a restricted, non-owner `firefly_audit_writer`, with no audit-guard membership, private audit schema USAGE, schema CREATE, superuser or bypass-RLS capability. Configure its existing database connection explicitly; do not run business requests as the installer.

After V19, the business owner grants only the following business-table privileges. The published audit installer separately supplies the canonical audit function privileges. Grant the completed-audit accessor only according to that published contract; these statements do not replace it.

```sql
GRANT USAGE ON SCHEMA public TO firefly_audit_writer;
GRANT SELECT ON public.loan_application, public.application_status,
    public.application_sub_status, public.submission_channel, public.simulation,
    public.loan_application_intake, public.application_party, public.role_code TO firefly_audit_writer;
GRANT INSERT ON public.loan_application, public.loan_application_intake, public.application_party TO firefly_audit_writer;
GRANT UPDATE (application_status_id) ON public.application_status TO firefly_audit_writer;
GRANT UPDATE (application_sub_status_id) ON public.application_sub_status TO firefly_audit_writer;
GRANT UPDATE (submission_channel_id) ON public.submission_channel TO firefly_audit_writer;
GRANT UPDATE (simulation_id) ON public.simulation TO firefly_audit_writer;
GRANT UPDATE (intake_id) ON public.loan_application_intake TO firefly_audit_writer;
GRANT UPDATE (role_code_id) ON public.role_code TO firefly_audit_writer;
GRANT UPDATE (application_party_id) ON public.application_party TO firefly_audit_writer;
GRANT EXECUTE ON FUNCTION public.verify_governed_primary_applicant(UUID,UUID,UUID,UUID) TO firefly_audit_writer;
```

PostgreSQL requires UPDATE privilege on at least one column for each `SELECT FOR UPDATE`. Each existing Spring Data intake repository intentionally locks its retained row through the same owned transaction. The primary-key column grants above satisfy that PostgreSQL requirement without granting changes to terms, active flags, status codes or evidence. They do authorize primary-key UPDATE attempts; preserve the V18 immutable-association trigger, which rejects even an `intake_id = intake_id` update. Existing foreign keys continue to constrain lookup keys. Do not add table-wide UPDATE or DELETE to satisfy row locking.

The DRAFT status row serializes first creation; the association is re-read after that lock so a committed same-action retry returns the persisted receipt. Configure exactly one active `PRIMARY_APPLICANT` role through the existing role-code catalog before enabling intake. No role identifier is hardcoded or created by the business command. The same root inserts the application, primary applicant and immutable intake evidence. Income, employment and ownership-share fields remain unknown until supplied by a later operation.

V19 requires a matching primary link for every new intake, protects its identity against legacy mutation, and supplies a typed retained-link integrity check. Creation compares the complete stored primary entity with the planned audited effect; replay allows subsequently supplied economic data while checking the original protected identity. The primary applicant is read under a shared row lock for both creation and replay. Its key-only UPDATE privilege supports that PostgreSQL lock; V19 still prohibits changing the retained key or identity. Disabling or changing the primary role makes the governed replay fail closed; it does not erase the historical application. V19 does not backfill historical applications or fabricate a past audit event: an older incomplete intake must fail closed until a separately audited repair operation is implemented. No statement here authorizes later approval, offer, contract or disbursement transitions.

Qualification must inspect every column on all seven locked tables: only the named key column may have UPDATE privilege. It must also attempt a runtime no-op key UPDATE against a retained association and require SQLSTATE 23514 from the unchanged V18 immutability guard. Verify that primary-link identity changes and deletion fail with PZL02 while replay retains the original link. Force a zero-row primary insert and require rollback of the application, primary link, intake, audit event and outbox. Hosted source tests, owned PostgreSQL execution, native signed JWT/three-service transport, publication and deployment adoption are separate acceptance stages.
