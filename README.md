# db-evolve

[![](https://img.shields.io/maven-central/v/com.github.oliverselinger/db-evolve)]()
[![](https://jitpack.io/v/oliverselinger/db-evolve.svg)](https://jitpack.io/#oliverselinger/db-evolve)
[![CircleCI](https://circleci.com/gh/oliverselinger/db-evolve.svg?style=svg)](https://circleci.com/gh/oliverselinger/db-evolve)
[![codecov](https://codecov.io/gh/oliverselinger/db-evolve/branch/main/graph/badge.svg?token=K68CRS0CFQ)](https://codecov.io/gh/oliverselinger/db-evolve)

Database schema evolution with plain SQL.

## Features

* **Lightweight**. Single class, just 2 tables for persistence.
* **Fail fast**. Failed migration prevents app from starting.
* **Multi-node compatible**. Coordination between nodes with locking.
* **Checksum** validation. Prevents from accidental changes.
* **No dependencies**.
* **No reflection**.

DbEvolve updates a database from one version to a next using migrations. A migration is written in SQL with database-specific syntax. Those script files must be made available inside a directory called `sql` in classpath.

Migrations are versioned by providing a version number and a unique description, plus a calculated checksum behind the scenes. The version number is used to apply migrations in order. The checksum validation detects accidental changes of already applied migrations.

All statements of a script file run within a single database transaction. Successful migration executions are recorded in db. This makes sure migration scripts run only once.

Inspired by [Dbolve](https://github.com/cinemast/dbolve) and [Flyway](https://flywaydb.org)

## Getting started

1.  Add the dependency
```xml
<dependency>
    <groupId>com.github.oliverselinger</groupId>
    <artifactId>db-evolve</artifactId>
    <version>0.6.1</version>
</dependency>
```

and the Jitpack repository
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

2. Put your database schema inside the directory `sql` and name your sql files after this naming convention:

`V<Version>__<Description>.sql`

`<Version>` must be an integer literal. This number is used to determine the order of execution. Files are sorted numerically, not lexicographically.

3. Instantiate `DbEvolve`, which can then be used to start the migration of your database.

```java
DbEvolve dbEvolve = new DbEvolve(dataSource);
dbEvolve.migrate();
```

On instantiation of DbEvolve, the two tables `DB_EVOLVE` and `DB_EVOLVE_LOCK` are created for state control. If they already exist, creation is simply skipped.  

## Scripts

The migration scripts can be written in SQL with database-specific syntax. As default, statements are delimited by `;` at the end of the line. Blank lines and single line comments are ignored.

You can override the default delimiter to parse functions, like PL/SQL, as a whole. For that, just put the keyword `DELIMITER <your_delimiter>` in a line before the function starts. Then at the of the function put `<your_delimiter>` in a new line.

```sql
DELIMITER ##
CREATE FUNCTION public.my_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    ... statements ending with ;
END
$$;
##
```

### Placeholders

Inside SQL scripts you can specify placeholders which will be replaced by the provided values before execution.
This is useful in case you want to use same sql scripts for multiple db vendors. E.g. Date/Time types for H2 is TIMESTAMP and for Oracle it is DATE.
So you could have the following statement:

```sql
CREATE TABLE PERSON (
    ID VARCHAR(36) NOT NULL,
    CREATED_DATE ${datetime},
    PRIMARY KEY (ID)
);
```

When you execute the migration pass a `Map<String, String>` that contains a value for each placeholder:

For H2:
```sql
Map<String, String> replacements = new HashMap<>();
replacements.put("datetime", "TIMESTAMP");
dbEvolve.migrate(replacements);
```

For Oracle:
```sql
Map<String, String> replacements = new HashMap<>();
replacements.put("datetime", "DATE");
dbEvolve.migrate(replacements);
```

## FAQ

#### Sql comments

Single line comments indicated with `--` are simply skipped. There is no special handling for multi-line comments.

#### Can I change the content of an already applied migration script?

No! A content change also changes the checksum, and you will receive an exception. However, instead create a new script.

#### Is it running in production?

No.

#### What Java versions are supported?

Requires Java 11+.

#### What databases are supported?

It is tested with Oracle, Postgres, MySql and H2.
