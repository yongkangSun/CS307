create table chinese_blocks
       (movieid  int not null,
        rn       int not null,
        block    varchar(3) not null,
        constraint chinese_blocks_pk
               primary key(block, movieid, rn),
        constraint chinese_blocks_fk
               foreign key(movieid, rn)
               references chinese_titles(movieid, rn)
               on delete cascade)
;
create or replace function chinese_title_split()
returns trigger
as $$
begin
  if tg_op = 'update'
  then
    delete from chinese_blocks
    where movieid = old.movieid
      and rn = old.rn;
  end if;
  insert into chinese_blocks(movieid, rn, block)
  select new.movieid, new.rn, bl
  from chinese_split(new.title) as bl;
  return null;
end;
$$ language plpgsql
;
create trigger chinese_titles_trg
after insert or update on chinese_titles
for each row
  execute procedure chinese_title_split()
;
delete from chinese_titles
;
insert into chinese_titles(movieid, rn, title)
select movieid,
       row_number() over (partition by movieid order by title) as rn,
       title
from alt_titles
where ascii(title) between 19968 and 40959;
--
select * from chinese_blocks
;

