create table if not exists students
   (studentid varchar(10) not null primary key,
    name      varchar(50) not null,
    unique(name))
;
