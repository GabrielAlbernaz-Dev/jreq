# jREQ

**jREQ** is a lightweight, local-first desktop HTTP client inspired by Postman and Insomnia. It provides a responsive JavaFX workspace, immutable request model, asynchronous HTTP engine, SQLite persistence, Flyway migrations, and automated tests.

The workspace executes HTTP requests, renders response body/headers/raw data, saves requests individually or inside flat collections, and records both successful and failed executions. Collections, requests, and history can all be managed directly from the sidebar.

## Interface concept

```text
┌──────────────────────────────────────────────────────────────────────┐
│ ☰  jREQ  HTTP WORKSPACE                              Local      ⚙   │
├──────────────────┬───────────────────────────────────────────────────┤
│ COLLECTIONS   +  │ GET   Enter request URL          Save     Send  │
│ ROOT             ├───────────────────────────────────────────────────┤
│ GET Health       │ Params   Headers   Body   Auth                    │
│ Local API        │                                                   │
│   POST Create    │ enabled  key                 value                │
│                  │ Params   Headers   Body   Auth                    │
│                  │                                                   │
│                  │ enabled  key                 value                │
│ HISTORY          ├───────────────────────────────────────────────────┤
│ GET Health       │ Response                       200 · 42 ms · 2 KB │
│                  │ Body     Headers     Raw                          │
│                  │                                                   │
│ + New request    │ Send a request to view the response              │
├──────────────────┴───────────────────────────────────────────────────┤
│ ● Ready                                                    UTF-8     │
└──────────────────────────────────────────────────────────────────────┘
```

## Stack

- Java 21+
- JavaFX 21+ with FXML and JavaFX CSS
- Maven
- AtlantaFX (Primer Dark base theme)
- Java `HttpClient` with `sendAsync`
- SQLite with Xerial SQLite JDBC
- Flyway migrations
- Jackson
- SLF4J + Logback
- JUnit 5 + AssertJ

No Spring, Hibernate, JPA, Lombok, reactive framework, embedded web server, or dependency-injection framework is used.

## Requirements

- JDK 21 or newer (the compiler always targets Java 21)
- Maven 3.9+
- A graphical desktop session to run JavaFX

Check the active toolchain with:

```bash
java -version
mvn -version
```

## Run

```bash
mvn javafx:run
```

The initial window is 1280×800 and remains usable down to 760×560. Keyboard shortcuts:

- `Ctrl/Cmd + Enter`: trigger request sending
- `Ctrl/Cmd + B`: collapse or expand the sidebar
- `Ctrl/Cmd + S`: save the current request
- `Ctrl/Cmd + Shift + S`: save a copy of the current request

## Test

```bash
mvn clean test
```

The suite is entirely local. The HTTP integration test starts a loopback `HttpServer`; it never uses the internet.

## Package

```bash
mvn clean package
```

This creates `target/jreq.jar` and validates the complete build. The JavaFX Maven plugin already carries launcher and runtime-image names for the future `jlink`/`jpackage` pipeline. A production runtime image is deliberately not generated in this setup: Flyway currently ships as an automatic module, so the packaging phase will first need a small module-normalization step (or an application-image layout that keeps third-party libraries on the classpath). This avoids pretending that a platform-specific installer is portable before CI covers Windows, Linux, and macOS.

## Architecture

Code is organized by feature and layer only where the boundary is already useful:

```text
src/main/java/com/jreq/
├── JReqApplication.java
├── bootstrap/
│   ├── AppDirectories.java
│   ├── ApplicationContext.java
│   ├── DatabaseInitializer.java
│   └── SceneManager.java
├── request/
│   ├── domain/                 immutable request/body/value models
│   ├── application/            workspace service, ports, and result types
│   ├── infrastructure/
│   │   ├── http/               Java HttpClient implementation
│   │   └── persistence/        JDBC collection, request, and history repositories
│   └── presentation/           ViewModel, UI coordination, dialogs, and sidebar rendering
└── shared/
    ├── concurrent/             asynchronous task execution boundary
    ├── database/               SQLite connection and transaction lifecycle
    ├── exception/              structured error categories
    ├── json/                   application ObjectMapper
    └── ui/                     responsive manager and reusable controls

src/main/resources/
├── css/                        theme, components, responsive modes
├── db/migration/               versioned Flyway SQL
├── fxml/main-view.fxml
├── application.properties
└── logback.xml
```

`ApplicationContext` is the composition root. It creates the mapper, database connection factory, migrations, repositories, HTTP executor, dedicated database executor, workspace service, ViewModel, and controller factory without exposing global mutable state. Controllers perform UI coordination only; they do not execute SQL or build HTTP requests directly.

## Local database

The data directory is created automatically on startup and never points into `src`, `target`, or the installation directory.

| System | Default location |
|---|---|
| Windows | `%APPDATA%/jREQ/jreq.db` |
| Linux | `${XDG_DATA_HOME}/jreq/jreq.db`, or `~/.local/share/jreq/jreq.db` |
| macOS | `~/Library/Application Support/jREQ/jreq.db` |

Each SQLite connection enables foreign keys, WAL journal mode, and a 5000 ms busy timeout. Flyway creates `app_setting`, `collection`, `saved_request`, and `request_history`; request definitions and history snapshots are stored as JSON while IDs, names, methods, URLs, collection links, outcomes, and timestamps stay queryable.

Collection names are unique, ignoring case. Request names are unique within their location: each collection and the root have independent namespaces. Deleting a collection moves its requests to the root by default; an explicit checkbox deletes them instead. Name collisions created by a move are resolved with numeric suffixes. History retains the 100 newest attempts and stores both successful responses and structured failures.

## Responsive behavior

`ResponsiveLayoutManager` is the single place that maps scene width to CSS state:

- compact below 900 px: the sidebar collapses automatically and request controls tighten;
- normal from 900 through 1300 px: the sidebar and full workspace remain visible;
- wide above 1300 px: navigation and primary workspace spacing expand modestly.

The request and response areas use a resizable vertical `SplitPane`, the URL field owns all remaining horizontal space, and there is no global horizontal scroller.

## Visual palette

| Role | Color |
|---|---|
| Main background | `#0B0B0D` |
| Main surface | `#141418` |
| Elevated surface | `#1C1C22` |
| Interactive surface | `#24242C` |
| Standard / subtle border | `#30303A` / `#24242B` |
| Primary / hover / pressed red | `#E53935` / `#F04440` / `#C62828` |
| Soft red | `#3A1718` |
| Primary / secondary / disabled text | `#F4F4F5` / `#B4B4BD` / `#70707A` |
| Success / warning / error / information | `#2FBF71` / `#F0A43B` / `#FF6464` / `#5DA9E9` |

The red accent is limited to Send, focus, selection, and small identity details. AtlantaFX provides the stable control baseline; project CSS owns the jREQ visual language.

## Technical decisions

- Request definitions and value objects are immutable records with defensive copies; `WorkspaceName` owns trimming, validation, and case-insensitive comparison for collection and request names.
- `RequestBody` is a compact discriminated model for none, JSON, and raw text; no hierarchy is needed yet.
- HTTP calls return a sealed success/failure result and stay asynchronous from end to end.
- HTTP and SQLite work never block the JavaFX Application Thread; UI properties are updated only after completion is marshalled back to JavaFX.
- Error categories are user-safe; technical failures are logged without credentials, full URLs, headers, cookies, or bodies.
- SQLite is the real persistence engine in both application and repository tests. Temporary files isolate tests without replacing the database technology.
- JDBC repositories own their SQL, row mapping, and transaction boundaries. `JdbcTransactionManager` centralizes connection, commit, rollback, and failure preservation; explicit transactions are limited to atomic multi-statement operations such as collection deletion and history trimming.
- FXML describes the screen composition; reusable, stateful controls stay as focused Java classes.
- Responsive thresholds are testable without opening a JavaFX window.

## Why JavaFX?

JavaFX makes jREQ a genuinely native desktop application while keeping Java as the single implementation stack. A native client is not constrained by browser CORS, integrates directly with local SQLite, and can later be distributed as platform-specific application images and installers through `jlink` and `jpackage`.

## Roadmap

1. Add environment-variable interpolation without persisting credentials.
2. Persist window, sidebar, and collection-expansion preferences in `app_setting`.
3. Add import/export for a small jREQ JSON format.
4. Normalize third-party modules and validate `jlink`/`jpackage` images in a platform CI matrix.
5. Add UI smoke tests for compact, normal, and wide viewport snapshots.
