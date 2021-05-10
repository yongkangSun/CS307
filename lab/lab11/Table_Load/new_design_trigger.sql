create or replace function new_design()
returns trigger
as $$
declare
  n_count        int;
  s_name         students.name%type;
begin
  s_name := new.name;
  if ascii(s_name) between 19968 and 40959
  then
    -- Chinese name
    new.name := trim(split_part(s_name, ',', 1));
    new.english_name := trim(split_part(s_name, ',', 2));
  else
    -- International student.
    -- Two cases : Same thing in Eastern/Western order
    -- or first name and surname
    with q as
     (select new.studentid,
             new.name,
             split_part(s_name, ',', 1) as part1,
             split_part(s_name, ',', 2) as part2)
      select count(*)
      into n_count
      from (select studentid,
                   name,
                   split_part(part1, ' ', n) as part
            from q
                 cross join generate_series(1, 20) n 
            except
            select studentid,
                   name,
                   split_part(part2, ' ', n) as part
            from q
                 cross join generate_series(1, 20) n) x;
     if n_count = 0
     then
       -- Comma separates the same name in different order
       -- Same case as Chinese name
       new.name := trim(split_part(s_name, ',', 1));
       new.english_name := trim(split_part(s_name, ',', 2));
     else
       -- Don't change name, modify for English name
       new.english_name := trim(upper(trim(split_part(s_name, ',', 1)))
                          || ' ' || trim(split_part(s_name, ',', 2)));
     end if;
  end if; 
  return new; -- modified
end;
$$ language plpgsql;

