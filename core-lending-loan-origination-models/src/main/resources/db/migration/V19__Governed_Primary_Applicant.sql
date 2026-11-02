-- Forward-only protection for the primary applicant created by governed intake.
DO $preflight$
BEGIN
    IF current_user <> session_user OR (
        SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid=c.relowner
        WHERE n.nspname='public' AND c.relkind='r' AND r.rolname=current_user
            AND c.relname IN ('application_party','loan_application_intake','role_code')
    ) <> 3 THEN
        RAISE EXCEPTION 'Exact lending owner installation required' USING ERRCODE='42501';
    END IF;
END
$preflight$;

CREATE FUNCTION public.verify_governed_primary_applicant(tenant UUID,application UUID,party UUID,primary_id UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $body$
    SELECT EXISTS (
        SELECT 1 FROM public.loan_application_intake i
        JOIN public.application_party p ON p.loan_application_id=i.loan_application_id AND p.party_id=i.party_id
        JOIN public.role_code r ON r.role_code_id=p.role_code_id
        WHERE i.resource_tenant_id=tenant AND i.loan_application_id=application AND i.party_id=party
            AND p.application_party_id=primary_id AND p.is_primary IS TRUE
            AND r.code='PRIMARY_APPLICANT' AND r.is_active IS TRUE
            AND p.created_at=i.created_at AT TIME ZONE 'UTC'
    )
$body$;
REVOKE ALL ON FUNCTION public.verify_governed_primary_applicant(UUID,UUID,UUID,UUID) FROM PUBLIC;

CREATE FUNCTION public.protect_governed_primary_applicant() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $body$
BEGIN
    IF TG_TABLE_SCHEMA <> 'public' OR TG_TABLE_NAME <> 'application_party' THEN
        RAISE EXCEPTION 'Exact application-party guard required' USING ERRCODE='23514';
    END IF;
    IF TG_OP='TRUNCATE' THEN
        IF EXISTS (SELECT 1 FROM public.loan_application_intake) THEN
            RAISE EXCEPTION 'Governed primary applicants must be retained' USING ERRCODE='PZL02';
        END IF;
        RETURN NULL;
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.is_primary IS TRUE AND EXISTS (
            SELECT 1 FROM public.loan_application_intake i WHERE i.loan_application_id=NEW.loan_application_id
        ) THEN
            RAISE EXCEPTION 'Governed primary applicant requires an audited transition' USING ERRCODE='PZL02';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP NOT IN ('UPDATE','DELETE') THEN
        RAISE EXCEPTION 'Exact application-party operation required' USING ERRCODE='23514';
    END IF;
    IF OLD.is_primary IS TRUE AND EXISTS (
        SELECT 1 FROM public.loan_application_intake i WHERE i.loan_application_id=OLD.loan_application_id
    ) THEN
        IF TG_OP='DELETE' THEN
            RAISE EXCEPTION 'Governed primary applicant identity is immutable' USING ERRCODE='PZL02';
        END IF;
        IF ROW(NEW.application_party_id,NEW.loan_application_id,NEW.party_id,NEW.role_code_id,NEW.is_primary,NEW.created_at)
            IS DISTINCT FROM ROW(OLD.application_party_id,OLD.loan_application_id,OLD.party_id,OLD.role_code_id,OLD.is_primary,OLD.created_at) THEN
            RAISE EXCEPTION 'Governed primary applicant identity is immutable' USING ERRCODE='PZL02';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF NEW.is_primary IS TRUE
        AND (OLD.is_primary IS NOT TRUE OR NEW.loan_application_id IS DISTINCT FROM OLD.loan_application_id)
        AND EXISTS (
        SELECT 1 FROM public.loan_application_intake i WHERE i.loan_application_id=NEW.loan_application_id
    ) THEN
        RAISE EXCEPTION 'Governed primary applicant requires an audited transition' USING ERRCODE='PZL02';
    END IF;
    RETURN NEW;
END
$body$;
REVOKE ALL ON FUNCTION public.protect_governed_primary_applicant() FROM PUBLIC;
CREATE TRIGGER protect_governed_primary_applicant_row
BEFORE INSERT OR UPDATE OR DELETE ON public.application_party
FOR EACH ROW EXECUTE FUNCTION public.protect_governed_primary_applicant();
CREATE TRIGGER protect_governed_primary_applicant_truncate
BEFORE TRUNCATE ON public.application_party
FOR EACH STATEMENT EXECUTE FUNCTION public.protect_governed_primary_applicant();

CREATE FUNCTION public.require_governed_primary_applicant() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $body$
DECLARE primary_id UUID;
BEGIN
    SELECT p.application_party_id INTO primary_id FROM public.application_party p
        WHERE p.loan_application_id=NEW.loan_application_id AND p.party_id=NEW.party_id AND p.is_primary IS TRUE;
    IF primary_id IS NULL OR NOT public.verify_governed_primary_applicant(
        NEW.resource_tenant_id,NEW.loan_application_id,NEW.party_id,primary_id
    ) THEN
        RAISE EXCEPTION 'Exact retained primary applicant required' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END
$body$;
REVOKE ALL ON FUNCTION public.require_governed_primary_applicant() FROM PUBLIC;
CREATE CONSTRAINT TRIGGER require_governed_primary_applicant
AFTER INSERT ON public.loan_application_intake
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION public.require_governed_primary_applicant();
