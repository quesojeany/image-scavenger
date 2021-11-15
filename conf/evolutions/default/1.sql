# --- !Ups

create table "images" (
  "id" bigint generated by default as identity(start with 1) not null primary key,
  "name" varchar not null,
  "path" varchar not null,
  "detectionEnabled" boolean not null default false
);

create table "annotations" (
    "id" bigint generated by default as identity(start with 1) not null primary key,
    "name" varchar not null,
    "imageId" bigint not null,
     CONSTRAINT FK_ANNOTATIONS_IMAGES FOREIGN KEY (imageId) references images(id)

);

# --- !Downs
drop table "annotations" if exists;
drop table "images" if exists;


