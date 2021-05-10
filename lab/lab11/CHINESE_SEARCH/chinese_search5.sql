create or replace function chinese_candidates(p_user_input text)
returns table(movieid int)
as $$
   select movieid
   from (select movieid, rank() over (order by hits desc) rnk
         from (select cb.movieid, count(*) as hits
               from chinese_split(p_user_input) searched
                    join chinese_blocks cb
                     on cb.block = searched 
               group by cb.movieid) x) y
   where rnk = 1;
$$ language sql
;
