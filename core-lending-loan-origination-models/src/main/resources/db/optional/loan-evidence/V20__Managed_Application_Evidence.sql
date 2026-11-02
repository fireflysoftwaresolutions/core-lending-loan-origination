-- Optional owner installation. V1-V19 and existing unmanaged document records remain unchanged.
BEGIN;
SET LOCAL search_path=pg_catalog;
DO $$ DECLARE guard_oid OID;owner_oid OID;BEGIN
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='loan_evidence_guard' AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication;
 SELECT relowner INTO STRICT owner_oid FROM pg_catalog.pg_class WHERE oid='public.application_document'::pg_catalog.regclass AND relkind='r';
 IF current_user<>session_user OR owner_oid<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname=current_user)
  OR NOT pg_catalog.pg_has_role(current_user,guard_oid,'SET') OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=guard_oid)
  OR pg_catalog.pg_has_role('firefly_audit_writer',guard_oid,'MEMBER')
  OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_writer' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
  OR pg_catalog.current_setting('server_encoding')<>'UTF8'
  OR (SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname IN('loan_evidence','loan_evidence_private') AND nspowner=guard_oid)<>2
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n,LATERAL pg_catalog.aclexplode(coalesce(n.nspacl,pg_catalog.acldefault('n',n.nspowner))) a WHERE n.nspname IN('loan_evidence','loan_evidence_private') AND a.grantee<>guard_oid)
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN('loan_evidence','loan_evidence_private')) THEN
  RAISE EXCEPTION 'Exact independent Loan evidence installer required' USING ERRCODE='42501';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace JOIN pg_catalog.pg_roles r ON r.oid=p.proowner
  WHERE n.nspname='public' AND p.proname='firefly_audit_require_completed' AND p.proargtypes='2950 2950 20 25 16'::pg_catalog.oidvector AND p.prosecdef AND r.rolname='firefly_audit_guard'
  AND p.proconfig=ARRAY['search_path=pg_catalog'] AND p.provolatile='v' AND pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p.prosrc,'UTF8')),'hex')='c839ff938bc0695dd61a0d9a80ec537860025f051bc9e02f19572b91e6b0dccf'
  AND pg_catalog.has_function_privilege('loan_evidence_guard',p.oid,'EXECUTE')) THEN
  RAISE EXCEPTION 'Published completed canonical audit deployment required' USING ERRCODE='42501';
 END IF;
END $$;
GRANT USAGE ON SCHEMA public TO loan_evidence_guard;
GRANT SELECT,REFERENCES ON public.application_document,public.loan_application,public.loan_application_intake,public.application_party,public.role_code,public.document_type,public.application_status,public.application_sub_status TO loan_evidence_guard;
GRANT EXECUTE ON FUNCTION public.verify_governed_primary_applicant(UUID,UUID,UUID,UUID) TO loan_evidence_guard;
SET LOCAL ROLE loan_evidence_guard;
CREATE TABLE loan_evidence.association(
 association_id UUID PRIMARY KEY CHECK(association_id<>'00000000-0000-0000-0000-000000000000'),
 resource_tenant_id UUID NOT NULL,action_id UUID NOT NULL,application_id UUID NOT NULL,party_id UUID NOT NULL,
 document_resource_tenant_id UUID NOT NULL,document_id UUID NOT NULL,document_type_id UUID NOT NULL,document_type_code VARCHAR(50) NOT NULL,
 file_name VARCHAR(200) NOT NULL CHECK(file_name<>'' AND file_name=pg_catalog.btrim(file_name) AND file_name NOT IN('.','..') AND pg_catalog.strpos(file_name,'/')=0 AND pg_catalog.strpos(file_name,pg_catalog.chr(92))=0 AND file_name !~ '[[:cntrl:]]'),
 media_type VARCHAR(100) NOT NULL CHECK((media_type COLLATE "C") ~ '^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$'),
 content_length BIGINT NOT NULL CHECK(content_length BETWEEN 1 AND 2097152),
 content_digest TEXT NOT NULL CHECK((content_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 document_intent_digest TEXT NOT NULL CHECK((document_intent_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 document_stored_seconds BIGINT NOT NULL,document_stored_nanos INTEGER NOT NULL CHECK(document_stored_nanos BETWEEN 0 AND 999999999),
 intent_digest TEXT NOT NULL CHECK((intent_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),effect_digest TEXT NOT NULL CHECK((effect_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 primary_party_id UUID NOT NULL,primary_role_id UUID NOT NULL,initial_status_id UUID NOT NULL,initial_sub_status_id UUID NOT NULL,
 binding_id UUID NOT NULL,binding_version BIGINT NOT NULL CHECK(binding_version>0),binding_digest TEXT NOT NULL CHECK((binding_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,actor_client_id TEXT NOT NULL,actor_platform_tenant_id TEXT NOT NULL,
 policy_revision VARCHAR(128) NOT NULL CHECK((policy_revision COLLATE "C") ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),attached_at TIMESTAMPTZ NOT NULL,
 audit_context_nonce UUID NOT NULL,audit_event_id UUID NOT NULL,audit_sequence BIGINT NOT NULL CHECK(audit_sequence>0),audit_record_hash TEXT NOT NULL CHECK((audit_record_hash COLLATE "C") ~ '^[0-9a-f]{64}$'),
 CHECK('00000000-0000-0000-0000-000000000000'::uuid<>ALL(ARRAY[resource_tenant_id,action_id,application_id,party_id,document_resource_tenant_id,document_id,document_type_id,primary_party_id,primary_role_id,initial_status_id,initial_sub_status_id,binding_id,audit_context_nonce,audit_event_id])),
 CHECK((document_type_code COLLATE "C") ~ '^[A-Z][A-Z0-9_]{0,49}$'),
 UNIQUE(resource_tenant_id,action_id),UNIQUE(resource_tenant_id,audit_event_id),UNIQUE(application_id,document_resource_tenant_id,document_id),
 FOREIGN KEY(association_id) REFERENCES public.application_document(application_document_id) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(application_id) REFERENCES public.loan_application(loan_application_id) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(document_type_id) REFERENCES public.document_type(document_type_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE FUNCTION loan_evidence_private.text(value TEXT) RETURNS BYTEA LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE bytes BYTEA;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value)>4096 THEN RAISE EXCEPTION 'Bounded Loan evidence framing required' USING ERRCODE='23514';END IF;
 bytes:=pg_catalog.convert_to(value,'UTF8');RETURN pg_catalog.int4send(pg_catalog.octet_length(bytes))||bytes;
END $$;
CREATE FUNCTION loan_evidence_private.require_graph(p_association UUID) RETURNS VOID LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c loan_evidence.association%ROWTYPE;d public.application_document%ROWTYPE;a public.loan_application%ROWTYPE;p public.application_party%ROWTYPE;r public.role_code%ROWTYPE;t public.document_type%ROWTYPE;
 intent TEXT;effect TEXT;seconds BIGINT;nanos INTEGER;completed RECORD;
BEGIN
 SELECT * INTO STRICT c FROM loan_evidence.association WHERE association_id=p_association;
 SELECT * INTO STRICT d FROM public.application_document WHERE application_document_id=p_association;
 SELECT * INTO STRICT a FROM public.loan_application WHERE loan_application_id=c.application_id;
 SELECT * INTO STRICT p FROM public.application_party WHERE application_party_id=c.primary_party_id;
 SELECT * INTO STRICT r FROM public.role_code WHERE role_code_id=c.primary_role_id;
 SELECT * INTO STRICT t FROM public.document_type WHERE document_type_id=c.document_type_id;
 IF NOT public.verify_governed_primary_applicant(c.resource_tenant_id,c.application_id,c.party_id,c.primary_party_id)
  OR (p.loan_application_id,p.party_id,p.role_code_id,p.is_primary) IS DISTINCT FROM(c.application_id,c.party_id,c.primary_role_id,true)
  OR r.code<>'PRIMARY_APPLICANT' OR r.is_active IS NOT TRUE OR t.code<>c.document_type_code OR t.is_active IS NOT TRUE
  OR a.application_status_id<>c.initial_status_id OR a.application_sub_status_id<>c.initial_sub_status_id
  OR NOT EXISTS(SELECT 1 FROM public.application_status WHERE application_status_id=c.initial_status_id AND code='DRAFT' AND is_active IS TRUE)
  OR NOT EXISTS(SELECT 1 FROM public.application_sub_status WHERE application_sub_status_id=c.initial_sub_status_id AND code='PENDING_DOCUMENTS' AND is_active IS TRUE) THEN
  RAISE EXCEPTION 'Exact current managed application party and active type required' USING ERRCODE='23514';
 END IF;
 intent:=pg_catalog.encode(pg_catalog.sha256(loan_evidence_private.text('FIREFLY_LOAN_EVIDENCE_ASSOCIATION_1')
  ||pg_catalog.uuid_send(c.action_id)||pg_catalog.uuid_send(c.application_id)||pg_catalog.uuid_send(c.party_id)||pg_catalog.uuid_send(c.document_resource_tenant_id)||pg_catalog.uuid_send(c.document_id)
  ||loan_evidence_private.text(c.file_name)||loan_evidence_private.text(c.document_type_code)||loan_evidence_private.text(c.media_type)||loan_evidence_private.text(c.content_digest)||loan_evidence_private.text(c.document_intent_digest)
  ||pg_catalog.int8send(c.content_length)||pg_catalog.int8send(c.document_stored_seconds)||pg_catalog.int4send(c.document_stored_nanos)),'hex');
 seconds:=pg_catalog.floor(extract(epoch FROM c.attached_at))::bigint;nanos:=((extract(epoch FROM c.attached_at)-seconds)*1000000000)::integer;
 effect:=pg_catalog.encode(pg_catalog.sha256(loan_evidence_private.text('FIREFLY_LOAN_EVIDENCE_EFFECT_1')||loan_evidence_private.text(intent)
  ||loan_evidence_private.text(c.resource_tenant_id::text)||loan_evidence_private.text(c.association_id::text)||loan_evidence_private.text(c.document_type_id::text)
  ||loan_evidence_private.text(c.primary_party_id::text)||loan_evidence_private.text(c.primary_role_id::text)||loan_evidence_private.text(c.initial_status_id::text)||loan_evidence_private.text(c.initial_sub_status_id::text)
  ||loan_evidence_private.text(c.binding_id::text)||loan_evidence_private.text(c.binding_version::text)||loan_evidence_private.text(c.binding_digest)
  ||loan_evidence_private.text(c.actor_issuer)||loan_evidence_private.text(c.actor_subject)||loan_evidence_private.text(c.actor_client_id)||loan_evidence_private.text(c.actor_platform_tenant_id)||loan_evidence_private.text(c.policy_revision)
  ||pg_catalog.int8send(seconds)||pg_catalog.int4send(nanos)),'hex');
 IF c.intent_digest IS DISTINCT FROM intent OR c.effect_digest IS DISTINCT FROM effect
  OR (d.loan_application_id,d.document_id,d.document_type_id,d.is_mandatory,d.is_received,d.received_at,d.document_name,d.file_size_bytes,d.mime_type,d.verification_status,d.verified_by,d.verified_at,d.rejection_reason,d.expiry_date,d.created_at,d.updated_at)
   IS DISTINCT FROM(c.application_id,c.document_id,c.document_type_id,NULL::boolean,true,c.attached_at AT TIME ZONE 'UTC',c.file_name,c.content_length,c.media_type,'PENDING'::text,NULL::varchar,NULL::timestamp,NULL::varchar,NULL::date,c.attached_at AT TIME ZONE 'UTC',c.attached_at AT TIME ZONE 'UTC') THEN
  RAISE EXCEPTION 'Exact immutable managed association graph required' USING ERRCODE='23514';
 END IF;
 SELECT * INTO STRICT completed FROM public.firefly_audit_require_completed(c.audit_context_nonce,c.audit_event_id,c.audit_sequence,c.audit_record_hash,false);
 IF (completed.resource_tenant_id,completed.service,completed.resource_type,completed.resource_id,completed.event_id,completed.actor_issuer,completed.actor_subject,completed.actor_client_id,completed.actor_platform_tenant_id,completed.actor_staff_realm,completed.actor_staff_id,completed.action,completed.outcome,completed.process_id,completed.correlation_id,completed.causation_id,completed.occurred_seconds,completed.occurred_nanos,completed.evidence_digest,completed.policy_revision)
  IS DISTINCT FROM(c.resource_tenant_id,'core-loan-origination'::text,'loan-application'::text,c.application_id,c.audit_event_id,c.actor_issuer,c.actor_subject,c.actor_client_id,c.actor_platform_tenant_id,NULL::text,NULL::uuid,'lending.application.evidence.attach'::text,'COMPLETED'::text,c.action_id,c.action_id,NULL::uuid,seconds,nanos,c.effect_digest,c.policy_revision) THEN
  RAISE EXCEPTION 'Exact completed managed association audit required' USING ERRCODE='23514';
 END IF;
END $$;
CREATE FUNCTION loan_evidence_private.graph_insert() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF TG_TABLE_SCHEMA='public' THEN
  IF EXISTS(SELECT 1 FROM loan_evidence.association WHERE association_id=NEW.application_document_id) THEN PERFORM loan_evidence_private.require_graph(NEW.application_document_id);END IF;
 ELSE PERFORM loan_evidence_private.require_graph(NEW.association_id);END IF;RETURN NULL;
END $$;
CREATE FUNCTION loan_evidence_private.immutable() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF TG_TABLE_SCHEMA<>'public' THEN
  RAISE EXCEPTION 'Managed association cannot be mutated or deleted' USING ERRCODE='23514';
 ELSIF TG_OP='TRUNCATE' THEN
  IF EXISTS(SELECT 1 FROM loan_evidence.association) THEN RAISE EXCEPTION 'Managed association cannot be mutated or deleted' USING ERRCODE='23514';END IF;
 ELSIF EXISTS(SELECT 1 FROM loan_evidence.association WHERE association_id=OLD.application_document_id) THEN
  RAISE EXCEPTION 'Managed association cannot be mutated or deleted' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD;ELSIF TG_OP='UPDATE' THEN RETURN NEW;ELSE RETURN NULL;END IF;
END $$;
CREATE CONSTRAINT TRIGGER loan_evidence_graph AFTER INSERT ON loan_evidence.association DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_evidence_private.graph_insert();
CREATE TRIGGER loan_evidence_immutable BEFORE UPDATE OR DELETE ON loan_evidence.association FOR EACH ROW EXECUTE FUNCTION loan_evidence_private.immutable();
CREATE TRIGGER loan_evidence_no_truncate BEFORE TRUNCATE ON loan_evidence.association FOR EACH STATEMENT EXECUTE FUNCTION loan_evidence_private.immutable();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA loan_evidence_private FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA loan_evidence FROM PUBLIC;
DO $$ BEGIN
 EXECUTE pg_catalog.format('GRANT USAGE ON SCHEMA loan_evidence_private TO %I',session_user);
 EXECUTE pg_catalog.format('GRANT EXECUTE ON FUNCTION loan_evidence_private.graph_insert(),loan_evidence_private.immutable() TO %I',session_user);
END $$;
GRANT USAGE ON SCHEMA loan_evidence TO firefly_audit_writer;
GRANT SELECT,INSERT ON loan_evidence.association TO firefly_audit_writer;
RESET ROLE;
CREATE CONSTRAINT TRIGGER loan_evidence_legacy_graph AFTER INSERT ON public.application_document DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION loan_evidence_private.graph_insert();
CREATE TRIGGER loan_evidence_legacy_immutable BEFORE UPDATE OR DELETE ON public.application_document FOR EACH ROW EXECUTE FUNCTION loan_evidence_private.immutable();
CREATE TRIGGER loan_evidence_legacy_no_truncate BEFORE TRUNCATE ON public.application_document FOR EACH STATEMENT EXECUTE FUNCTION loan_evidence_private.immutable();
GRANT SELECT,INSERT ON public.application_document TO firefly_audit_writer;
GRANT SELECT ON public.loan_application,public.loan_application_intake,public.application_party,public.role_code,public.document_type,public.application_status,public.application_sub_status TO firefly_audit_writer;
GRANT EXECUTE ON FUNCTION public.verify_governed_primary_applicant(UUID,UUID,UUID,UUID) TO firefly_audit_writer;
GRANT UPDATE(application_party_id) ON public.application_party TO firefly_audit_writer;
GRANT UPDATE(role_code_id) ON public.role_code TO firefly_audit_writer;
GRANT UPDATE(application_status_id) ON public.application_status TO firefly_audit_writer;
GRANT UPDATE(application_sub_status_id) ON public.application_sub_status TO firefly_audit_writer;
GRANT UPDATE(loan_application_id) ON public.loan_application TO firefly_audit_writer;
GRANT UPDATE(document_type_id) ON public.document_type TO firefly_audit_writer;
SET LOCAL ROLE loan_evidence_guard;
DO $$ BEGIN
 EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION loan_evidence_private.graph_insert(),loan_evidence_private.immutable() FROM %I',session_user);
 EXECUTE pg_catalog.format('REVOKE USAGE ON SCHEMA loan_evidence_private FROM %I',session_user);
END $$;
RESET ROLE;
COMMIT;
