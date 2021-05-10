with t as (select cast('邋遢大王奇遇记' as varchar) as title)
select title,
       substring(title, n, 1) as one_char,
       substring(title, n, 2) as two_chars,
       substring(title, n, 3) as three_chars
from t
     cross join generate_series(1, 200) n
where length(coalesce(substring(title, n, 1), '')) > 0
order by n
;
