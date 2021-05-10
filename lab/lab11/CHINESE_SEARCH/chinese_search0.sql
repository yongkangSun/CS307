drop table if exists chinese_titles
;
create table chinese_titles
as
select movieid,
       row_number() over (partition by movieid order by title) as rn,
       title
from alt_titles
where ascii(title) between 19968 and 40959;
