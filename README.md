# db-evolve

[![](https://img.shields.io/maven-central/v/com.github.oliverselinger/db-evolve)]()
[![](https://jitpack.io/v/oliverselinger/db-evolve.svg)](https://jitpack.io/#oliverselinger/db-evolve)
[![CircleCI](https://circleci.com/gh/oliverselinger/db-evolve.svg?style=svg)](https://circleci.com/gh/oliverselinger/db-evolve)
[![codecov](https://codecov.io/gh/oliverselinger/db-evolve/branch/master/graph/badge.svg)](https://codecov.io/gh/oliverselinger/db-evolve)

Database schema evolution with plain SQL.

## Features

* **Persistence** Requires only one database-table.
* **Reliable** execution. Guarantees each script is applied exactly once.
* **Multi-node compatible**. Coordination between nodes with locking.
* **Lightweight**. Very small code base.
* **No dependencies**.
* **No reflection**.

DbEvolve updates a database from one version to a next using migrations. A migrations is written in SQL with database-specific syntax. Those script files must be made available inside a directory called `sql` in classpath.

Migrations are versioned by providing a unique name by you and a calculated hash of the content behind the scenes. That allows that each script is applied exactly once. 

All statements of a script file run within a single database transaction.

Inspired by this project: https://github.com/cinemast/dbolve

## Getting started

1.  Add the dependency
```xml
<dependency>
    <groupId>com.github.oliverselinger</groupId>
    <artifactId>db-evolve</artifactId>
    <version>1.0.0</version>
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

3. Instantiate `DbEvolve`, which can then be used to start the migration of your database.

```java
DbEvolve dbEvolve = new DbEvolve(dataSource);
dbEvolve.migrate();
```