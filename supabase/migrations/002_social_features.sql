-- OVION 002: social features (likes, comments, follows, messages, notifications, storage)
-- شغّله في Supabase > SQL Editor بعد supabase_schema.sql (آمن لو اتشغل أكتر من مرة)

-- ===== posts: media + counters =====
alter table public.posts add column if not exists image_url text;
alter table public.posts add column if not exists likes_count int not null default 0;
alter table public.posts add column if not exists comments_count int not null default 0;
alter table public.posts add column if not exists shares_count int not null default 0;
alter table public.posts drop constraint if exists posts_content_length;
alter table public.posts add constraint posts_content_length check (char_length(content) between 0 and 10000);

alter table public.profiles add column if not exists followers_count int not null default 0;
alter table public.profiles add column if not exists following_count int not null default 0;
alter table public.profiles add column if not exists is_private boolean not null default false;

-- ===== likes =====
create table if not exists public.likes (
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
create index if not exists likes_user_idx on public.likes(user_id);

-- ===== comments (with replies) =====
create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  author_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  parent_id uuid references public.comments(id) on delete cascade,
  content text not null check (char_length(content) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index if not exists comments_post_idx on public.comments(post_id, created_at);

-- ===== shares =====
create table if not exists public.shares (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);

-- ===== follows =====
create table if not exists public.follows (
  follower_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  following_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);
create index if not exists follows_following_idx on public.follows(following_id);

-- ===== messages =====
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  content text not null check (char_length(content) between 1 and 4000),
  read_at timestamptz,
  created_at timestamptz not null default now(),
  check (sender_id <> receiver_id)
);
create index if not exists messages_pair_idx on public.messages(sender_id, receiver_id, created_at desc);
create index if not exists messages_receiver_idx on public.messages(receiver_id, read_at);

-- ===== notifications =====
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade,
  type text not null check (type in ('like','comment','follow')),
  post_id uuid references public.posts(id) on delete cascade,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications(user_id, created_at desc);

-- ===== counter + notification triggers (security definer: العدادات مش بتتعدل من التطبيق) =====
create or replace function public.on_like_change() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set likes_count = likes_count + 1 where id = new.post_id;
    insert into public.notifications(user_id, actor_id, type, post_id)
      select author_id, new.user_id, 'like', new.post_id from public.posts
      where id = new.post_id and author_id <> new.user_id;
    return new;
  else
    update public.posts set likes_count = greatest(likes_count - 1, 0) where id = old.post_id;
    return old;
  end if;
end $$;
drop trigger if exists likes_aiud on public.likes;
create trigger likes_aiud after insert or delete on public.likes for each row execute function public.on_like_change();

create or replace function public.on_comment_change() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    insert into public.notifications(user_id, actor_id, type, post_id)
      select author_id, new.author_id, 'comment', new.post_id from public.posts
      where id = new.post_id and author_id <> new.author_id;
    return new;
  else
    update public.posts set comments_count = greatest(comments_count - 1, 0) where id = old.post_id;
    return old;
  end if;
end $$;
drop trigger if exists comments_aiud on public.comments;
create trigger comments_aiud after insert or delete on public.comments for each row execute function public.on_comment_change();

create or replace function public.on_share_insert() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.posts set shares_count = shares_count + 1 where id = new.post_id;
  return new;
end $$;
drop trigger if exists shares_ai on public.shares;
create trigger shares_ai after insert on public.shares for each row execute function public.on_share_insert();

create or replace function public.on_follow_change() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    update public.profiles set following_count = following_count + 1 where id = new.follower_id;
    update public.profiles set followers_count = followers_count + 1 where id = new.following_id;
    insert into public.notifications(user_id, actor_id, type) values (new.following_id, new.follower_id, 'follow');
    return new;
  else
    update public.profiles set following_count = greatest(following_count - 1, 0) where id = old.follower_id;
    update public.profiles set followers_count = greatest(followers_count - 1, 0) where id = old.following_id;
    return old;
  end if;
end $$;
drop trigger if exists follows_aiud on public.follows;
create trigger follows_aiud after insert or delete on public.follows for each row execute function public.on_follow_change();

-- منع تعديل العدادات من العميل
create or replace function public.protect_counters() returns trigger language plpgsql set search_path = '' as $$
begin
  -- pg_trigger_depth() > 1 يعني التعديل جاي من trigger داخلي (العدادات)، فنسيبه
  if pg_trigger_depth() <= 1 and auth.uid() is not null then
    if tg_table_name = 'posts' then
      new.likes_count := old.likes_count; new.comments_count := old.comments_count; new.shares_count := old.shares_count;
      new.author_id := old.author_id;
    else
      new.followers_count := old.followers_count; new.following_count := old.following_count;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists posts_protect on public.posts;
create trigger posts_protect before update on public.posts for each row execute function public.protect_counters();
drop trigger if exists profiles_protect on public.profiles;
create trigger profiles_protect before update on public.profiles for each row execute function public.protect_counters();

-- ===== RLS =====
alter table public.likes enable row level security;
alter table public.comments enable row level security;
alter table public.shares enable row level security;
alter table public.follows enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;

drop policy if exists "likes read" on public.likes;
create policy "likes read" on public.likes for select to authenticated using (true);
drop policy if exists "likes insert own" on public.likes;
create policy "likes insert own" on public.likes for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "likes delete own" on public.likes;
create policy "likes delete own" on public.likes for delete to authenticated using (auth.uid() = user_id);

drop policy if exists "comments read" on public.comments;
create policy "comments read" on public.comments for select to authenticated using (true);
drop policy if exists "comments insert own" on public.comments;
create policy "comments insert own" on public.comments for insert to authenticated with check (auth.uid() = author_id);
drop policy if exists "comments delete own" on public.comments;
create policy "comments delete own" on public.comments for delete to authenticated using (auth.uid() = author_id);

drop policy if exists "shares read" on public.shares;
create policy "shares read" on public.shares for select to authenticated using (true);
drop policy if exists "shares insert own" on public.shares;
create policy "shares insert own" on public.shares for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "follows read" on public.follows;
create policy "follows read" on public.follows for select to authenticated using (true);
drop policy if exists "follows insert own" on public.follows;
create policy "follows insert own" on public.follows for insert to authenticated with check (auth.uid() = follower_id);
drop policy if exists "follows delete own" on public.follows;
create policy "follows delete own" on public.follows for delete to authenticated using (auth.uid() = follower_id);

drop policy if exists "messages participants read" on public.messages;
create policy "messages participants read" on public.messages for select to authenticated
  using (auth.uid() = sender_id or auth.uid() = receiver_id);
drop policy if exists "messages send as self" on public.messages;
create policy "messages send as self" on public.messages for insert to authenticated with check (auth.uid() = sender_id);
drop policy if exists "messages receiver marks read" on public.messages;
create policy "messages receiver marks read" on public.messages for update to authenticated
  using (auth.uid() = receiver_id) with check (auth.uid() = receiver_id);

drop policy if exists "notifications read own" on public.notifications;
create policy "notifications read own" on public.notifications for select to authenticated using (auth.uid() = user_id);
drop policy if exists "notifications update own" on public.notifications;
create policy "notifications update own" on public.notifications for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

grant select, insert, delete on public.likes, public.comments, public.follows to authenticated;
grant select, insert on public.shares, public.messages to authenticated;
grant update (read_at) on public.messages to authenticated;
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
grant update (display_name, username, bio, avatar_url, cover_url, is_private) on public.profiles to authenticated;
grant update (content, image_url) on public.posts to authenticated;

-- ===== Realtime =====
do $$ begin
  begin alter publication supabase_realtime add table public.messages; exception when others then null; end;
  begin alter publication supabase_realtime add table public.notifications; exception when others then null; end;
end $$;

-- ===== Storage (صور المنشورات والبروفايل) =====
insert into storage.buckets (id, name, public) values ('media', 'media', true) on conflict (id) do nothing;
drop policy if exists "media public read" on storage.objects;
create policy "media public read" on storage.objects for select using (bucket_id = 'media');
drop policy if exists "media upload own folder" on storage.objects;
create policy "media upload own folder" on storage.objects for insert to authenticated
  with check (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "media delete own folder" on storage.objects;
create policy "media delete own folder" on storage.objects for delete to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text);
