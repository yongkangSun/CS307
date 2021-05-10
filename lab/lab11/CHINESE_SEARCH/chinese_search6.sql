select title,
       also_known_as,
       origin,
       string_agg(case c.credited_as
                    when 'D' then trim(p.surname
                                       || ' ' || coalesce(p.first_name, ''))
                    else null
                   end, ',') directors,
       string_agg(case c.credited_as
                    when 'A' then trim(p.surname
                                       || ' ' || coalesce(p.first_name, ''))
                    else null
                   end, ',') actors
from (select cm.title,
             string_agg(at.title, ',') also_known_as,
             co.country_name || ', ' || m.year_released origin,
             m.movieid
      from (select ct.movieid, ct.title
            from chinese_candidates('施普灵河') cc
                 join chinese_titles ct
                  on ct.movieid = cc.movieid
            where ct.rn = 1) cm
           join movies m
             on m.movieid = cm.movieid
           join countries co
             on co.country_code = m.country
           left join alt_titles at
             on at.movieid = cm.movieid
            and at.title <> cm.title
       group by cm.title,
                co.country_name,
                m.year_released,
                m.movieid) x
     left join credits c
       on c.movieid = x.movieid
     left join people p
       on p.peopleid = c.peopleid
group by title,
         also_known_as,
         origin
order by origin
;
