create trigger students_trg
before insert on students
for each row
 when new.english_name is null  -- Only for insert statements
                                -- unaware of the new table structure
  execute procedure new_design();
