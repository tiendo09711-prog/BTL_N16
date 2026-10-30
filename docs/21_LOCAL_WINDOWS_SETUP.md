# Local Windows environment

This machine was updated on 2026-10-30 for the Java desktop auction project.
The client uses JavaFX and WebSocket; the server stores data through JDBC in
the MariaDB installation bundled with XAMPP.

## Installed tools

| Tool | Version | Location |
| --- | --- | --- |
| Project Java compiler/runtime | Eclipse Temurin 25.0.4.1+1 | `%LOCALAPPDATA%\BTL16\tools\jdk-25.0.4.1` |
| Maven | 3.9.16 | `C:\Program Files\Apache\maven\apache-maven-3.9.16` |
| Node.js / npm | 24.13.0 / 11.6.2 | `C:\Program Files\nodejs` |
| XAMPP MariaDB | 10.4.32 | `C:\xampp\mysql` |
| VS Code Java support | Already installed | Extension Pack for Java |

The Java 25 compiler/runtime was copied from the already-installed VS Code Java
extension bundle into the project tools directory. This avoids depending on the
extension's installation path. The other build tools were already available.
Java 17 cannot build this version: `pom.xml` targets Java 25.
Packaging EXEs additionally requires a full JDK 25+ with `jpackage` and `jlink`.
JavaFX, MySQL Connector/J, Jackson, WebSocket and SLF4J JARs are downloaded by
Maven into `target/dependency`. The Node runners have no external npm packages,
so `npm install` is not needed.

## Start working

Open the project folder in VS Code and create a new terminal. Workspace settings
select the installed Java 25 for the project and new terminals, without changing
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
running the new schema initializer:
`%LOCALAPPDATA%\BTL16\backups\btl_16_before_update_20261030-095551.sql`.
The SQL backup is retained independently of old source-code copies and is
outside this project, so `mvn clean` does not remove it. No database reset,
demo seeding, or account creation is required.
Starting the new server applies its additive schema migrations to existing data.

The database self-test uses its own randomly named `btl16_verify_*` database
and deletes only that temporary test database when it finishes.

The paths in `.vscode/settings.json` are specific to this Windows account.
Update them when moving the repository to another machine. The activation
script finds the project JDK under the current account's `%LOCALAPPDATA%`.

## Source repository

The source was updated to `upstream/main`, commit
`de891d312f1ed235d45b19abfec3c2e8982499b2` (2026-10-29).
`origin` remains the original `tiendo09711-prog/BTL_N16` repository;
`upstream` is `phuoc2005nd-a11y/BTL-LTM`. Publish this project's updates to
`origin/main`; do not push local setup changes to `upstream`.

To check for future updates without replacing local changes:

```powershell
git fetch upstream main
git log --oneline HEAD..upstream/main
git merge upstream/main
git push origin main
```

Legacy `target_classes/` and `target_test-classes/` compiled outputs are removed
from Git and ignored. Maven builds the current source into `target/classes`
and `target/test-classes`; do not restore old `.class` files into the project.

## Media and Vietnamese voice

FFmpeg and FFprobe 8.1.2 are present in `tools/media/` and pass the checksums in
`tools/prepare-media.ps1`. Piper and the Vietnamese voice model are installed in
`tools/speech/`. The model passes the SHA-256 check in
`tools/prepare-client-voice.ps1`; an actual synthesis also produced a nonempty WAV.
These downloaded executables and models are ignored by Git.

## Verification on 2026-10-30

A fresh Maven build removes the previous `target/` outputs and compiles the new
source with Java 25. The complete test run and clean-build rechecks are recorded
in `target/qa/final-verification.json`: 24 of 28 groups pass.
`MarketplaceUiSelfTest` passes after the clean rebuild. The following upstream
checks still fail; the application is not certified free of functional bugs:

- `BidExperienceUiSelfTest`: expects a JOIN while the mock still has an
  unacknowledged LEAVE request.
- `LiveAuctionRoomUiSelfTest`: compact-column overlap/overflow assertion.
- `AuctionButtonsUiSelfTest`: the test casts a new BorderPane form to GridPane.
- `MysqlStorageSelfTest`: wallet-balance assertion reports `99.00` instead of
  the test's expected `106.70`.

Core/network/media self-tests, packaging unit checks, and the other two real
MySQL test groups pass. The existing database initializes successfully and
retains its 5 users, 4 products, 3 auctions and 8 bids.

Both JavaFX desktop applications are launched from the rebuilt `target/classes`.
The live WebSocket PING reports `UP`, the client connects, and the protected
media HTTP server responds on port 8891. Runtime logs and process IDs are in
`target/run/`. Close both applications before another clean build or before
running `npm.cmd run dev`, to avoid deleting live classes or occupying the same
server ports twice.
