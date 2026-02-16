alter table signature add column signer_identity text;

update signature
set signer_identity = convert_from(signature, 'UTF8')::jsonb -> 'details' ->> 'signerIdentity'
where signature is not null;

create index idx_signature_signer_identity on signature(signer_identity);

create index idx_provenance_record_data_type on provenance_record((metadata ->> 'dataType'));

create index idx_provenance_record_data_id on provenance_record((metadata ->> 'dataId'));
