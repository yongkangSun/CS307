create or replace function chinese_split(p_chinese_text text)
returns table(char_block varchar(3))
as $$
with t as (select p_chinese_text as chinese_text)
select distinct
       case n
         when 1 then one_char
         when 2 then two_chars
         else three_chars
       end
from (select substring(chinese_text, n, 1) as one_char,
             substring(chinese_text, n, 2) as two_chars,
             substring(chinese_text, n, 3) as three_chars
      from t
           cross join generate_series(1, 200) n
      where length(coalesce(substring(chinese_text, n, 1), '')) > 0) x
     cross join generate_series(1, 3) n
;
$$ language sql
;
