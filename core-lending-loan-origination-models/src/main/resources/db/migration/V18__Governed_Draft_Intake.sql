-- Forward-only DRAFT intake association. This does not implement later lending transitions.
-- Install as the exact existing public loan-application owner, without SET ROLE.
DO $preflight$
BEGIN
    IF current_user <> session_user OR NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class a JOIN pg_catalog.pg_namespace n ON n.oid=a.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid=a.relowner
        WHERE n.nspname='public' AND a.relname='loan_application' AND a.relkind='r' AND r.rolname=current_user
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class a JOIN pg_catalog.pg_namespace n ON n.oid=a.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid=a.relowner
        WHERE n.nspname='public' AND a.relname='simulation' AND a.relkind='r' AND r.rolname=current_user
    ) THEN RAISE EXCEPTION 'Exact lending owner installation required' USING ERRCODE='42501'; END IF;
    IF pg_catalog.to_regclass('public.loan_application_intake') IS NOT NULL
        OR pg_catalog.to_regprocedure('public.protect_governed_loan_application()') IS NOT NULL
        OR pg_catalog.to_regprocedure('public.protect_loan_application_intake()') IS NOT NULL THEN
        RAISE EXCEPTION 'Lending intake installation collision' USING ERRCODE='23514';
    END IF;
END
$preflight$;

CREATE TABLE public.loan_application_intake (
    intake_id UUID PRIMARY KEY,
    resource_tenant_id UUID NOT NULL,
    request_id UUID NOT NULL,
    loan_application_id UUID NOT NULL UNIQUE REFERENCES public.loan_application(loan_application_id),
    party_id UUID NOT NULL,
    simulation_id UUID NOT NULL REFERENCES public.simulation(simulation_id),
    product_id UUID NOT NULL,
    requested_amount NUMERIC(19,2) NOT NULL CHECK (requested_amount > 0),
    term INTEGER NOT NULL CHECK (term > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    purpose VARCHAR(50),
    intent_digest TEXT NOT NULL CHECK (intent_digest ~ '^[0-9a-f]{64}$'),
    binding_id UUID NOT NULL,
    binding_version BIGINT NOT NULL CHECK (binding_version > 0),
    binding_digest TEXT NOT NULL CHECK (binding_digest ~ '^[0-9a-f]{64}$'),
    audit_event_id UUID NOT NULL UNIQUE,
    audit_record_hash TEXT NOT NULL CHECK (audit_record_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (resource_tenant_id, request_id)
);

-- Definer scope is only an owner-controlled existence/shape guard, with no caller-supplied SQL.
-- It keeps unrelated legacy CRUD independent of reader grants on the new private association.
CREATE FUNCTION public.protect_governed_loan_application() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $body$
BEGIN
    IF TG_TABLE_SCHEMA <> 'public' OR TG_TABLE_NAME <> 'loan_application' THEN
        RAISE EXCEPTION 'Exact governed application guard required' USING ERRCODE='23514';
    END IF;
    IF TG_OP='TRUNCATE' THEN
        IF EXISTS (SELECT 1 FROM public.loan_application_intake) THEN
            RAISE EXCEPTION 'Governed lending applications are immutable' USING ERRCODE='PZL01';
        END IF;
        RETURN NULL;
    END IF;
    IF TG_OP NOT IN ('UPDATE','DELETE') THEN
        RAISE EXCEPTION 'Exact governed application operation required' USING ERRCODE='23514';
    END IF;
    IF EXISTS (SELECT 1 FROM public.loan_application_intake i WHERE i.loan_application_id=OLD.loan_application_id) THEN
        RAISE EXCEPTION 'Governed lending applications require an audited transition' USING ERRCODE='PZL01';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$body$;
REVOKE ALL ON FUNCTION public.protect_governed_loan_application() FROM PUBLIC;

CREATE FUNCTION public.protect_loan_application_intake() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $body$
BEGIN
    IF TG_TABLE_SCHEMA <> 'public' OR TG_TABLE_NAME <> 'loan_application_intake' OR TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Lending intake evidence is immutable' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.loan_application a
        JOIN public.application_status s ON s.application_status_id=a.application_status_id
        JOIN public.application_sub_status u ON u.application_sub_status_id=a.application_sub_status_id
        JOIN public.submission_channel c ON c.submission_channel_id=a.submission_channel_id
        JOIN public.simulation simulation ON simulation.simulation_id=a.simulation_id
        WHERE a.loan_application_id=NEW.loan_application_id AND a.simulation_id=NEW.simulation_id
            AND a.loan_purpose IS NOT DISTINCT FROM NEW.purpose
            AND a.created_at=NEW.created_at AT TIME ZONE 'UTC'
            AND a.application_date=(NEW.created_at AT TIME ZONE 'UTC')::date
            AND s.code='DRAFT' AND s.is_active IS TRUE
            AND u.code='PENDING_DOCUMENTS' AND u.is_active IS TRUE
            AND c.code='ONLINE' AND c.is_active IS TRUE
            AND simulation.product_type='PERSONAL_LOAN' AND simulation.product_id=NEW.product_id
            AND simulation.requested_amount=NEW.requested_amount AND simulation.term=NEW.term
            AND simulation.currency=NEW.currency AND simulation.purpose IS NOT DISTINCT FROM NEW.purpose
    ) THEN RAISE EXCEPTION 'Exact DRAFT intake and retained simulation required' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END
$body$;
REVOKE ALL ON FUNCTION public.protect_loan_application_intake() FROM PUBLIC;

CREATE TRIGGER protect_governed_loan_application_row
BEFORE UPDATE OR DELETE ON public.loan_application FOR EACH ROW
EXECUTE FUNCTION public.protect_governed_loan_application();
CREATE TRIGGER protect_governed_loan_application_truncate
BEFORE TRUNCATE ON public.loan_application FOR EACH STATEMENT
EXECUTE FUNCTION public.protect_governed_loan_application();
CREATE TRIGGER protect_loan_application_intake_row
BEFORE INSERT OR UPDATE OR DELETE ON public.loan_application_intake FOR EACH ROW
EXECUTE FUNCTION public.protect_loan_application_intake();
CREATE TRIGGER protect_loan_application_intake_truncate
BEFORE TRUNCATE ON public.loan_application_intake FOR EACH STATEMENT
EXECUTE FUNCTION public.protect_loan_application_intake();
