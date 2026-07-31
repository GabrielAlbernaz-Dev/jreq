# jREQ

**A local-first desktop HTTP client for building, sending, organizing, and revisiting API requests.**

jREQ is a native JavaFX application for developers who want a focused HTTP workspace without an account, browser runtime, or remote project storage. Requests, collections, and execution history are stored locally in SQLite, while network and database work remain off the JavaFX Application Thread.

> **Project status:** jREQ is under active development. The current version is intended to run from source; platform installers are not published yet.

[Quick start](#quick-start) · [Using jREQ](#using-jreq) · [Configuration](#configuration) · [Development](#development) · [Architecture](#architecture)

## Why jREQ?

- **Local-first:** the workspace lives in a SQLite database on your machine.
- **Native desktop workflow:** no browser CORS restrictions and no embedded web server.
- **Focused request editor:** methods, query parameters, headers, JSON, and raw text bodies.
- **Organized work:** save requests at the root or inside collections, then rename, move, duplicate, or delete them.
- **Automatic history:** revisit successful responses and structured failures without recreating a request.
- **Responsive JavaFX interface:** compact, normal, and wide layouts share the same black-and-red visual identity.

## Current features

- HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, and `OPTIONS`.
- Enabled or disabled query parameters and headers.
- Body types: none, JSON, and raw text.
- Asynchronous execution with configurable connection and request timeout.
- Response status, duration, size, headers, body, and raw views, with automatic or manually selected JSON, XML, and HTML formatting.
- Root requests and flat collections with case-insensitive name uniqueness.
- Request rename, move, duplicate, and delete operations.
- Collection create, rename, and delete operations.
- Reusable global and collection-scoped environments with `{{variable}}` interpolation.
- Selectable global variables, per-context environment selection, nested references, and masked secrets.
- Local history for the 100 newest attempts, including failures.
- Unsaved-change protection when navigating between requests.
- SQLite persistence managed through versioned Flyway migrations.

Authentication helpers, import/export, and packaged installers are not implemented yet. Their visible controls are placeholders for future work.

## Quick start

### Requirements

- JDK 21 or newer.
- Maven 3.9 or newer.
- A graphical desktop session capable of running JavaFX.

Check the active toolchain:

```bash
java -version
mvn -version
```

### Run from source

```bash
git clone https://github.com/GabrielAlbernaz-Dev/jreq.git
cd jreq
mvn javafx:run
```

Maven downloads the required JavaFX, SQLite, Flyway, Jackson, jsoup, and AtlantaFX dependencies on the first run. The local data directory and database are created automatically when jREQ starts.

## Using jREQ

### Send your first request

1. Start jREQ and select **New request** in the sidebar.
2. Choose an HTTP method and enter a URL, for example `https://httpbin.org/get`.
3. Add query parameters, headers, or a body when the endpoint requires them.
4. Select **Send** or press `Ctrl/Cmd + Enter`.
5. Inspect the response body, headers, raw response, status, duration, and size.

The request editor is divided into these tabs:

| Tab | Purpose |
| --- | --- |
| **Params** | Add query-string entries and temporarily disable individual rows. |
| **Headers** | Add request headers and toggle rows without deleting them. |
| **Body** | Send no body, JSON, or raw text. |
| **Auth** | Reserved for future authentication helpers. Add authorization headers manually for now. |

When a JSON or raw body is selected, jREQ supplies an appropriate `Content-Type` unless the request already defines one.

### Save a request

Use **Save** or press `Ctrl/Cmd + S` to store the active request. A new request can be saved at the workspace root or in an existing collection. Saving an already stored request updates it in place.

Open the menu beside **Save**, or press `Ctrl/Cmd + Shift + S`, to choose a destination explicitly. Request and collection names are trimmed and compared case-insensitively within their scope.

### Organize collections

- Use the add action in the sidebar to create a collection.
- Right-click a collection to rename or delete it.
- Right-click a saved request to rename, move, duplicate, or delete it.
- Deleting a collection moves its requests to the workspace root by default. Select the destructive option in the confirmation dialog only when the contained requests should also be deleted.
- If moving requests to the root creates a naming conflict, jREQ keeps both by adding a numeric suffix such as `(2)`.

Collections are intentionally flat in the current version; nested collections are not supported yet.

### Use environments and variables

Use the environment selector in the top bar to choose a context for the open request, or select **Manage environments…** to edit variables.

- **Globals** are used when **Globals only** is selected.
- **Global environments** and **collection environments** are grouped by scope in the selector, but can be selected for any request.
- The scope identifies where an environment is organized and managed; it does not block using that environment elsewhere.
- The root and every collection remember their own selected environment. Select **Globals only** to use Globals without a named environment.
- Selecting a named environment uses only that environment's variables; Globals are not used as fallback.

Reference variables with double braces, such as `{{base_url}}`. jREQ resolves placeholders in the URL, enabled query parameter values, enabled header values, and JSON or text bodies. Variable values can reference other variables. Missing variables and reference cycles block the request before any network call and identify the affected keys without displaying their values.

While editing a request, variable tokens in the URL, parameter values, and header values are highlighted green when resolved and red when missing or cyclic. The context row keeps an aggregate warning, including references found in the request body.

Environment edits are staged in the manager until **Save** is selected. Variables can be disabled without being removed or marked as secret. Secret values are masked by default in the manager, but are still stored as local workspace data rather than encrypted credentials.

Saved requests retain their original placeholders. History records the request template and the environment context, not a second copy containing resolved secret values. Opening a history entry restores its collection and environment when they still exist.

### Revisit history

Every completed request attempt is added to local history, including transport failures, invalid responses, and other structured errors. jREQ retains the 100 newest entries.

- Select a history entry to open a detached snapshot of the request and recorded response.
- Save that snapshot if it should become part of the workspace.
- Right-click an entry to remove it, or use **Clear history** to remove all entries.

History is a record of what was executed. Opening it does not silently overwrite a saved request.

### Unsaved changes

When navigation would discard edits, jREQ offers **Save**, **Discard**, and **Cancel**. This guard applies when switching between new, saved, and historical requests.

## Keyboard shortcuts

| Action | Windows/Linux | macOS |
| --- | --- | --- |
| Send request | `Ctrl + Enter` | `Cmd + Enter` |
| Toggle sidebar | `Ctrl + B` | `Cmd + B` |
| Save | `Ctrl + S` | `Cmd + S` |
| Save to a destination | `Ctrl + Shift + S` | `Cmd + Shift + S` |

## Local data and privacy

jREQ has no account requirement and no application backend. Workspace data stays in a local SQLite database. HTTP requests are sent directly from the desktop application to the destination entered by the user.

| Platform | Default database path |
| --- | --- |
| Windows | `%APPDATA%/jREQ/jreq.db` |
| macOS | `~/Library/Application Support/jREQ/jreq.db` |
| Linux | `${XDG_DATA_HOME}/jreq/jreq.db`, or `~/.local/share/jreq/jreq.db` when `XDG_DATA_HOME` is unset |

The database uses foreign-key enforcement, write-ahead logging, and a busy timeout. Flyway applies schema migrations at startup. Back up the database file before manually inspecting or modifying production workspace data.

jREQ does not log credentials, tokens, authorization headers, cookies, sensitive request bodies, environment values, or global variable values. Saved request headers, bodies, global variables, and environment values are workspace data. Values marked as secret are masked in the interface but stored as plaintext in SQLite, so treat the database file as sensitive.

## Configuration

Runtime defaults live in [`src/main/resources/application.properties`](src/main/resources/application.properties):

| Property | Default | Description |
| --- | --- | --- |
| `application.name` | `jREQ` | Name displayed by the application. |
| `application.version` | `0.1.0-SNAPSHOT` | Version displayed in the window title. |
| `database.filename` | `jreq.db` | File name created inside the platform data directory. Paths are rejected. |
| `http.timeout.seconds` | `30` | Positive whole-number timeout used by the HTTP client and each request. |

These properties are loaded from the application classpath. After editing them, restart the application; rebuild it as well when running packaged classes. Missing or invalid required values stop startup with a configuration error rather than silently falling back to a different value.

## Development

### Technology

- Java 21 and JavaFX 21 with FXML.
- Maven for dependency management, tests, and builds.
- SQLite through JDBC for local persistence.
- Flyway for versioned database migrations.
- Java `HttpClient` for asynchronous HTTP execution.
- Jackson for JSON serialization.
- jsoup for HTML parsing and source formatting.
- AtlantaFX plus project CSS for the desktop theme.
- RichTextFX for inline variable-token feedback in the request URL editor.
- JUnit 5 and AssertJ for tests.

jREQ deliberately uses explicit composition and does not depend on Spring, Hibernate, JPA, Lombok, or a dependency-injection framework.

### Test and build

Run the complete test suite:

```bash
mvn clean test
```

Build the project:

```bash
mvn clean package
```

The package phase creates `target/jreq.jar`, but that JAR is not a standalone desktop distribution because its runtime dependencies and platform-specific JavaFX libraries are not bundled. Use `mvn javafx:run` during development. Native installers and supported distribution bundles remain roadmap work.

### Database migrations

Migration scripts live in [`src/main/resources/db/migration`](src/main/resources/db/migration). Add a new, incremented Flyway migration for every schema change. Never edit a migration that may already have been applied to a user's database, because Flyway validates its checksum at startup.

## Architecture

The application follows explicit layers with dependency direction toward the domain and application contracts:

```text
JavaFX + FXML
      │
MainController ── MainViewModel
                       │
                WorkspaceService
                  ┌────┴────┐
        Repository ports   HttpExecutor
                  │             │
          SQLite/JDBC      Java HttpClient
```

- `bootstrap` loads configuration, initializes the database, composes dependencies, and manages the JavaFX scene.
- `request/domain` contains immutable request, collection, history, and location models.
- `request/application` owns workspace and environment use cases, variable resolution, and repository and HTTP boundaries.
- `request/infrastructure` implements SQLite persistence and HTTP transport.
- `request/presentation` coordinates JavaFX state, dialogs, and controller bindings.
- `shared` contains focused concurrency, database, JSON, error, and reusable UI support.

Configuration and policy values that would otherwise be revalidated across layers use immutable value objects. For
example, `DatabaseFilename`, `HttpTimeout`, and `HistoryLimit` are validated once when created, so persistence and
HTTP adapters receive values that are already safe to use. Primitive and collection invariants that remain local to
a model use the shared `Constraints` API, while relational rules between fields stay with the record that owns them.

Database tasks and response formatting run on separate dedicated single-thread executors. HTTP execution uses `HttpClient.sendAsync`, and presentation updates are marshalled back to the JavaFX Application Thread. Multi-step persistence operations can use `JdbcTransactionManager`, which commits on success and rolls back on failure.

The composition root is [`ApplicationContext`](src/main/java/com/jreq/bootstrap/ApplicationContext.java); dependencies remain explicit and lifecycle-owned rather than globally mutable.

### Project layout

```text
src/
├── main/
│   ├── java/com/jreq/
│   │   ├── bootstrap/
│   │   ├── request/{domain,application,infrastructure,presentation}/
│   │   └── shared/
│   └── resources/{fxml,css,db/migration}/
└── test/java/com/jreq/
```

## Interface and responsive behavior

jREQ opens at `1280 × 800` and supports a minimum window size of `760 × 560`. Responsive behavior is centralized in `ResponsiveLayoutManager`:

| Mode | Window width | Behavior |
| --- | --- | --- |
| Compact | Below `900px` | Tighter spacing, narrower controls, and reduced secondary metadata. |
| Normal | `900px` through `1300px` | Default desktop workspace. |
| Wide | Above `1300px` | Wider sidebar, request spacing, and response area. |

The visual identity is intentionally black and red. Keep new components within the established palette:

| Role | Color |
| --- | --- |
| Background | `#0B0B0D` |
| Surface / elevated / interactive | `#141418` / `#1C1C22` / `#24242C` |
| Border / subtle border | `#30303A` / `#24242B` |
| Red / hover / pressed / soft | `#E53935` / `#F04440` / `#C62828` / `#3A1718` |
| Primary / secondary / disabled text | `#F4F4F5` / `#B4B4BD` / `#70707A` |
| Success / warning / error / info | `#2FBF71` / `#F0A43B` / `#FF6464` / `#5DA9E9` |

## Contributing

Issues and focused pull requests are welcome while the project is taking shape.

1. Open an issue describing the behavior, motivation, and expected user experience.
2. Fork the repository and create a focused branch.
3. Preserve the existing Java 21, JavaFX, Maven, SQLite, FXML, and Flyway stack.
4. Keep controllers limited to UI events and bindings; keep SQL and HTTP rules outside them.
5. Add or update tests for the changed behavior.
6. Run both required verification commands before opening a pull request:

   ```bash
   mvn clean test
   mvn clean package
   ```

When reporting a bug, include the operating system, JDK version, exact reproduction steps, expected behavior, and the relevant exception without credentials or sensitive request data. Use the [GitHub issue tracker](https://github.com/GabrielAlbernaz-Dev/jreq/issues).

## Roadmap

Near-term areas include authentication helpers, import/export, deeper request organization, and platform-specific packaging. Roadmap items are direction rather than release commitments; current behavior is documented in [Current features](#current-features).

## License

A project license has not been selected yet. Until a license is added, the repository is source-available for inspection but does not grant general permission to copy, modify, or redistribute the code. A license should be chosen before publishing jREQ as a redistributable open-source release.
