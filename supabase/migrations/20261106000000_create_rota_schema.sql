-- Schema correspondente às entidades JPA em backend/src/main/java/.../domain.
-- IF NOT EXISTS também acomoda um banco local criado anteriormente pelo Hibernate.
create table if not exists public.user_accounts (
    id uuid primary key,
    username varchar(80) not null unique,
    password_hash varchar(100) not null,
    role varchar(30) not null,
    enabled boolean not null,
    created_at timestamptz not null
);

create table if not exists public.agent_profiles (
    id uuid primary key,
    user_account_id uuid not null unique references public.user_accounts (id),
    type varchar(20) not null,
    entity_name varchar(140) not null
);

create table if not exists public.client_profiles (
    id uuid primary key,
    user_account_id uuid not null unique references public.user_accounts (id),
    rg varchar(30) not null unique,
    cpf varchar(14) not null unique,
    name varchar(120) not null,
    address varchar(240) not null,
    profession varchar(100) not null
);

create table if not exists public.cars (
    id uuid primary key,
    registration varchar(30) not null,
    year integer not null,
    make varchar(60) not null,
    model varchar(80) not null,
    plate varchar(12) not null
);

create table if not exists public.employments (
    id uuid primary key,
    client_profile_id uuid not null references public.client_profiles (id),
    employer varchar(140) not null,
    income numeric(12, 2) not null
);

create table if not exists public.rental_requests (
    id uuid primary key,
    client_profile_id uuid not null references public.client_profiles (id),
    car_id uuid not null references public.cars (id),
    evaluated_by_id uuid references public.agent_profiles (id),
    status varchar(35) not null,
    start_date date not null,
    end_date date not null,
    client_notes varchar(1000),
    financial_opinion varchar(1000),
    submitted_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists public.rental_contracts (
    id uuid primary key,
    rental_request_id uuid not null unique references public.rental_requests (id),
    car_id uuid not null references public.cars (id),
    owner_type varchar(20) not null,
    owner_name varchar(140) not null,
    executed_by_id uuid not null references public.agent_profiles (id),
    executed_at timestamptz not null
);

create table if not exists public.credit_contracts (
    id uuid primary key,
    rental_contract_id uuid not null unique references public.rental_contracts (id),
    bank_agent_id uuid not null references public.agent_profiles (id),
    amount numeric(14, 2) not null
);

create index if not exists idx_employments_client_profile_id on public.employments (client_profile_id);
create index if not exists idx_rental_requests_client_profile_id on public.rental_requests (client_profile_id);
create index if not exists idx_rental_requests_car_id on public.rental_requests (car_id);
create index if not exists idx_rental_requests_evaluated_by_id on public.rental_requests (evaluated_by_id);
create index if not exists idx_rental_contracts_car_id on public.rental_contracts (car_id);
create index if not exists idx_rental_contracts_executed_by_id on public.rental_contracts (executed_by_id);
create index if not exists idx_credit_contracts_bank_agent_id on public.credit_contracts (bank_agent_id);

-- A aplicação usa a API Spring como única interface de dados.
-- As tabelas ficam fora do alcance direto dos papéis anon/authenticated da Data API.
alter table public.user_accounts enable row level security;
alter table public.agent_profiles enable row level security;
alter table public.client_profiles enable row level security;
alter table public.cars enable row level security;
alter table public.employments enable row level security;
alter table public.rental_requests enable row level security;
alter table public.rental_contracts enable row level security;
alter table public.credit_contracts enable row level security;
