# db-evolve

[![](https://img.shields.io/maven-central/v/com.github.oliverselinger/db-evolve)]()
[![](https://jitpack.io/v/oliverselinger/db-evolve.svg)](https://jitpack.io/#oliverselinger/db-evolve)
[![CircleCI](https://circleci.com/gh/oliverselinger/db-evolve.svg?style=svg)](https://circleci.com/gh/oliverselinger/db-evolve)
[![codecov](https://codecov.io/gh/oliverselinger/db-evolve/branch/main/graph/badge.svg?token=K68CRS0CFQ)](https://codecov.io/gh/oliverselinger/db-evolve)

Database schema evolution with plain SQL.

## Features

* **Persistence** requires two database tables.
* **Reliable** execution. Guarantees each script is applied exactly once.
* **Multi-node compatible**. Coordination between nodes with locking.
* **Lightweight**. Very small code base.
* **No dependencies**.
* **No reflection**.

DbEvolve updates a database from one version to a next using migrations. A migrations is written in SQL with database-specific syntax. Those script files must be made available inside a directory called `sql` in classpath.

Migrations are versioned by providing a unique name by you and a calculated hash of the content behind the scenes. That allows that each script is applied exactly once. 

All statements of a script file run within a single database transaction.

Inspired by [Dbolve](https://github.com/cinemast/dbolve) and [Flyway](https://flywaydb.org)

## Getting started

1.  Add the dependency
```xml
<dependency>
    <groupId>com.github.oliverselinger</groupId>
    <artifactId>db-evolve</artifactId>
    <version>0.3.1</version>
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

`<Version>` must be an integer literal. This number is used to determine the order of execution (sorted numerically not lexicographically). 

Inside the file between each statement there must be at least one empty line. Empty lines are used to delimit the sql statements.

3. Instantiate `DbEvolve`, which can then be used to start the migration of your database.

```java
DbEvolve dbEvolve = new DbEvolve(dataSource);
dbEvolve.migrate();
```

On instantiation of DbEvolve the two tables `DB_EVOLVE` and `DB_EVOLVE_LOCK` for state control are created. If they already exist, creation is simply skipped.  

## Scripts

The migration scripts can be written in SQL with database-specific syntax. As default, statements are delimited by `;` at the end of the line. New lines and single line comments are ignored.

For statements where the end is not indicated by a semicolon, e.g. PL-SQL scripts, the end delimiter can be overridden by a specific single line comment to use an empty new line as end indicator. For example:

```
INSERT INTO TEST VALUES (1);

-- ###_NEW_LINE_END_DELIMITER_ON_###
BEGIN
dbms_output.put_line (‘Hello World..');
END;
/

INSERT INTO TEST VALUES (2);
```

This would result in the execution of 3 statements. First an insert, second the PL-SQL script and third again an insert.

## FAQ

#### Sql comments

Single line comments indicated with `--` are supported. Multi-line comments not!

#### Can I change the content of an already applied migration script?

No! A content change also changes the hash value, and you will receive an exception. However, instead create a new script.

#### Is it running in production?

No.

#### What Java versions are supported?

Requires Java 11+.

#### What databases are supported?

It is tested with Postgres, MySql and H2.
