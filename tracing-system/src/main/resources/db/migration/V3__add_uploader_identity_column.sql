alter table provenance_record add column uploader_identity text;

create index idx_provenance_record_uploader_identity on provenance_record(uploader_identity);
