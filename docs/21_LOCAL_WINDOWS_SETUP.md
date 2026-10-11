# Local Windows environment

This machine was configured on 2026-10-11 for the Java desktop auction project.
The client uses JavaFX and WebSocket; the server stores data through JDBC in
the MariaDB installation bundled with XAMPP.

## Installed tools

| Tool | Version | Location |
| --- | --- | --- |
| Project JDK | Eclipse Temurin 17.0.20.1+1 | `%LOCALAPPDATA%\BTL16\tools\jdk-17.0.20.1+1` |
| Maven | 3.9.16 | `C:\Program Files\Apache\maven\apache-maven-3.9.16` |
| Node.js / npm | 24.13.0 / 11.6.2 | `C:\Program Files\nodejs` |
| XAMPP MariaDB | 10.4.32 | `C:\xampp\mysql` |
| VS Code Java support | Already installed | Extension Pack for Java |

Only the project JDK was downloaded and installed; the other tools were already
available. The JDK ZIP was checked against the SHA-256 published by the Adoptium
release API. Download metadata is in `%LOCALAPPDATA%\BTL16\jdk-install.json`.
JavaFX, MySQL Connector/J, Jackson, WebSocket and SLF4J JARs are downloaded by
Maven into `target/dependency`. The Node runners have no external npm packages,
so `npm install` is not needed.

## Start working

Open the project folder in VS Code and create a new terminal. Workspace settings
select the installed JDK 17 for the project and new terminals, without changing
the system-wide Java installation or `JAVA_HOME`. The Java language server can
continue using the newer runtime bundled with its extension.

For an existing PowerShell terminal or one opened outside VS Code, activate the
environment from the project root:

```powershell
. .\scripts\activate-env.ps1
java --version
mvn.cmd --version
```

Start **MySQL** in the XAMPP Control Panel before starting the project. Apache is
not required. This project uses `127.0.0.1:3306`, database `btl_16`, and the local
credentials in `config/server.properties`. Do not expose this development
database to the Internet. Another MySQL installation exists under Laragon;
use XAMPP's client explicitly if you need to inspect this database.

```powershell
npm.cmd run dev
```

This builds the project, checks JDBC, updates the schema without resetting the
database, and starts the server plus one JavaFX client. The client endpoint is
`ws://127.0.0.1:8890/ws`, not a browser website. The legacy TCP port is `8888`.
Press Ctrl+C in the terminal to stop the project; XAMPP MySQL stays running.

Other useful commands:

```powershell
npm.cmd run build
npm.cmd test
npm.cmd run test:packaging
npm.cmd run dev:server
npm.cmd run client
npm.cmd run dist
```

## Existing database

The database already contained data before setup. A SQL backup was made before
running the schema initializer and retained under `%LOCALAPPDATA%\BTL16\backups\`.
A working copy also exists under `target/local-setup/backups/`; `mvn clean`
removes that working copy, but not the backup outside the project. No reset,
demo seeding, or account creation was performed on `btl_16`. Comparing SQL dumps
before and after setup confirmed that the existing schema and data are unchanged.

The database self-test uses its own randomly named `btl16_verify_*` database
and deletes only that temporary test database when it finishes.

The paths in `.vscode/settings.json` are specific to this Windows account.
Update them when moving the repository to another machine. The activation
script finds the project JDK under the current account's `%LOCALAPPDATA%`.
