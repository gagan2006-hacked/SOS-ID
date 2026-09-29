create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id),
    token_verifier varchar(100) not null unique,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    replaced_at timestamp with time zone,
    created_at timestamp with time zone not null
);
create index idx_refresh_tokens_user on refresh_tokens(user_id);
