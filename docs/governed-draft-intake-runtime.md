# Governed DRAFT intake runtime deployment

Keep the intake feature disabled until the owner installs V18 and the published audit protocol under its existing separate installer contract. V1–V17 remain unchanged. The application login must be a restricted, non-owner `firefly_audit_writer`, with no audit-guard membership, private audit schema USAGE, schema CREATE, superuser or bypass-RLS capability. Configure its existing database connection explicitly; do not run business requests as the installer.

After V18, the business owner grants only the following business-table privileges. The published audit installer separately supplies the canonical audit function privileges. Grant the completed-audit accessor only according to that published contract; these statements do not replace it.

```sql
GRANT USAGE ON SCHEMA public TO firefly_audit_writer;
GRANT SELECT ON public.loan_application, public.application_status,
    public.application_sub_status, public.submission_channel, public.simulation,
    public.loan_application_intake TO firefly_audit_writer;
GRANT INSERT ON public.loan_application, public.loan_application_intake TO firefly_audit_writer;
GRANT UPDATE (application_status_id) ON public.application_status TO firefly_audit_writer;
GRANT UPDATE (application_sub_status_id) ON public.application_sub_status TO firefly_audit_writer;
GRANT UPDATE (submission_channel_id) ON public.submission_channel TO firefly_audit_writer;
GRANT UPDATE (simulation_id) ON public.simulation TO firefly_audit_writer;
GRANT UPDATE (intake_id) ON public.loan_application_intake TO firefly_audit_writer;
```

PostgreSQL requires UPDATE privilege on at least one column for each `SELECT FOR UPDATE`. Each existing Spring Data intake repository intentionally locks its retained row through the same owned transaction. The primary-key column grants above satisfy that PostgreSQL requirement without granting changes to terms, active flags, status codes or evidence. They do authorize primary-key UPDATE attempts; preserve the V18 immutable-association trigger, which rejects even an `intake_id = intake_id` update. Existing foreign keys continue to constrain lookup keys. Do not add table-wide UPDATE or DELETE to satisfy row locking.

The DRAFT status row serializes first creation; the association is re-read after that lock so a committed same-action retry returns the persisted receipt. The intake association remains immutable to runtime and business owner. No statement here authorizes later approval, offer, contract or disbursement transitions.

Qualification must inspect every column on all five locked tables: only the named key column may have UPDATE privilege. It must also attempt a runtime no-op key UPDATE against a retained association and require SQLSTATE 23514 from the unchanged V18 immutability guard. Hosted source tests, owned PostgreSQL execution, native signed JWT/three-service transport, publication and deployment adoption are separate acceptance stages.
