-- اختبار صلاحيات RLS — شغّله في Supabase SQL Editor. كله داخل transaction وبيتلغي في الآخر (rollback).
-- لو أي assert فشل هتظهر رسالة خطأ بالاسم.
begin;

-- مستخدمين تجريبيين
insert into auth.users (id, instance_id, aud, role, email) values
  ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-000000000000','authenticated','authenticated','a@test.local'),
  ('00000000-0000-0000-0000-0000000000b2','00000000-0000-0000-0000-000000000000','authenticated','authenticated','b@test.local'),
  ('00000000-0000-0000-0000-0000000000c3','00000000-0000-0000-0000-000000000000','authenticated','authenticated','c@test.local');

create or replace function pg_temp.as_user(uid text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
end $$;

create or replace function pg_temp.must_fail(sql text, label text) returns void language plpgsql as $$
begin
  begin
    execute sql;
  exception when others then
    return;  -- اتمنع زي المتوقع
  end;
  raise exception 'FAILED (المفروض يتمنع): %', label;
end $$;

-- A ينشر منشور
select pg_temp.as_user('00000000-0000-0000-0000-0000000000a1');
insert into public.posts(id, author_id, content) values ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-0000000000a1','hello');

-- B مينفعش ينتحل A في إعجاب/تعليق/منشور
select pg_temp.as_user('00000000-0000-0000-0000-0000000000b2');
select pg_temp.must_fail($$insert into public.likes(post_id,user_id) values ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-0000000000a1')$$, 'انتحال إعجاب');
select pg_temp.must_fail($$insert into public.comments(post_id,author_id,content) values ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-0000000000a1','x')$$, 'انتحال تعليق');
select pg_temp.must_fail($$insert into public.posts(author_id,content) values ('00000000-0000-0000-0000-0000000000a1','x')$$, 'انتحال منشور');

-- B مينفعش يحذف/يعدّل منشور A (التعديل/الحذف بيتجاهل الصفوف بدل ما يطلع خطأ، فنتأكد إن الصف لسه زي ما هو)
delete from public.posts where id = '11111111-1111-1111-1111-111111111111';
update public.posts set content = 'hacked' where id = '11111111-1111-1111-1111-111111111111';
update public.profiles set display_name = 'hacked' where id = '00000000-0000-0000-0000-0000000000a1';

select pg_temp.as_user('00000000-0000-0000-0000-0000000000a1');
do $$ begin
  if (select content from public.posts where id = '11111111-1111-1111-1111-111111111111') <> 'hello' then
    raise exception 'FAILED: منشور A اتعدّل/اتحذف بواسطة B';
  end if;
  if (select display_name from public.profiles where id = '00000000-0000-0000-0000-0000000000a1') = 'hacked' then
    raise exception 'FAILED: بروفايل A اتعدّل بواسطة B';
  end if;
end $$;

-- العدادات مينفعش تتعدل من العميل
update public.posts set likes_count = 999 where id = '11111111-1111-1111-1111-111111111111';
do $$ begin
  if (select likes_count from public.posts where id = '11111111-1111-1111-1111-111111111111') <> 0 then
    raise exception 'FAILED: العداد اتعدّل من العميل';
  end if;
end $$;

-- الإعجاب الحقيقي بيزوّد العداد
select pg_temp.as_user('00000000-0000-0000-0000-0000000000b2');
insert into public.likes(post_id,user_id) values ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-0000000000b2');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000a1');
do $$ begin
  if (select likes_count from public.posts where id = '11111111-1111-1111-1111-111111111111') <> 1 then
    raise exception 'FAILED: عداد الإعجابات مش بيزيد';
  end if;
  if (select count(*) from public.notifications where user_id = '00000000-0000-0000-0000-0000000000a1' and type='like') <> 1 then
    raise exception 'FAILED: إشعار الإعجاب مش اتعمل';
  end if;
end $$;

-- الرسائل الخاصة: A -> B، و C مينفعش يشوفها أو ينتحل
select pg_temp.as_user('00000000-0000-0000-0000-0000000000a1');
insert into public.messages(sender_id, receiver_id, content) values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b2','سر');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000c3');
do $$ begin
  if (select count(*) from public.messages) <> 0 then raise exception 'FAILED: C شاف رسالة مش بتاعته'; end if;
end $$;
select pg_temp.must_fail($$insert into public.messages(sender_id, receiver_id, content) values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b2','x')$$, 'انتحال مرسل رسالة');

-- المتابعة
select pg_temp.as_user('00000000-0000-0000-0000-0000000000c3');
select pg_temp.must_fail($$insert into public.follows(follower_id, following_id) values ('00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b2')$$, 'انتحال متابعة');
insert into public.follows(follower_id, following_id) values ('00000000-0000-0000-0000-0000000000c3','00000000-0000-0000-0000-0000000000a1');

select 'كل اختبارات RLS نجحت ✓' as result;
rollback;
