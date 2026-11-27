-- OVION DATABASE
-- Supabase schema and security

create extension if not exists pgcrypto;

-- 1. User profiles
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default 'مستخدم OVION',
    username text unique,
    bio text default '',
    avatar_url text,
    cover_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 2. Posts
create table if not exists public.posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null default auth.uid()
        references public.profiles(id) on delete cascade,
    content text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint posts_content_length
        check (char_length(content) between 1 and 10000)
);

-- 3. Indexes
create index if not exists posts_created_at_idx
on public.posts(created_at desc);

create index if not exists posts_author_id_idx
on public.posts(author_id);

-- 4. Automatically create profile after registration
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (
        id,
        display_name
    )
    values (
        new.id,
        coalesce(
            new.raw_user_meta_data ->> 'display_name',
            'مستخدم OVION'
        )
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created
on auth.users;

create trigger on_auth_user_created
after insert on auth.users
for each row
execute procedure public.handle_new_user();

-- 5. Enable Row Level Security
alter table public.profiles enable row level security;
alter table public.posts enable row level security;

-- 6. Profile policies
drop policy if exists "Profiles are viewable"
on public.profiles;

create policy "Profiles are viewable"
on public.profiles
for select
to authenticated
using (true);

drop policy if exists "Users create own profile"
on public.profiles;

create policy "Users create own profile"
on public.profiles
for insert
to authenticated
with check (auth.uid() = id);

drop policy if exists "Users update own profile"
on public.profiles;

create policy "Users update own profile"
on public.profiles
for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

-- 7. Post policies
drop policy if exists "Authenticated users view posts"
on public.posts;

create policy "Authenticated users view posts"
on public.posts
for select
to authenticated
using (true);

drop policy if exists "Users create own posts"
on public.posts;

create policy "Users create own posts"
on public.posts
for insert
to authenticated
with check (auth.uid() = author_id);

drop policy if exists "Users update own posts"
on public.posts;

create policy "Users update own posts"
on public.posts
for update
to authenticated
using (auth.uid() = author_id)
with check (auth.uid() = author_id);

drop policy if exists "Users delete own posts"
on public.posts;

create policy "Users delete own posts"
on public.posts
for delete
to authenticated
using (auth.uid() = author_id);

-- 8. Permissions
grant usage on schema public to authenticated;

grant select, insert, update
on public.profiles to authenticated;

grant select, insert, update, delete
on public.posts to authenticated;
