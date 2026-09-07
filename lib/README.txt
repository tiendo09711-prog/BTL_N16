Runtime libraries are declared in pom.xml and downloaded by Maven:
- MySQL Connector/J 8.4.0 (verified with local XAMPP MariaDB)
- JavaFX
- Java-WebSocket
- Jackson

Run npm run build to copy runtime JARs into target/dependency.
Only the server and database tools connect through JDBC.
Set db.host, db.port, db.user and db.password in config/server.properties.
Setup/reset create schema without demo data. Register through the client.
See docs/05_CACH_CHAY_VSCODE_XAMPP.md.
