create table signature(
    id uuid primary key,
    signing_time timestamp with time zone not null,
    signature bytea not null
);

create table provenance_record(
    id uuid primary key references signature(id),
    manifest jsonb not null,
    metadata jsonb not null,
    files jsonb,
    created_at timestamp with time zone default now() not null
);

create table verification_log(
    id integer primary key generated always as identity,
    provenance_record_id uuid not null references provenance_record(id),
    created_at timestamp with time zone default now() not null,
    status boolean not null
);

create index index_verification_log_provenance_record_id
    on verification_log(provenance_record_id);
