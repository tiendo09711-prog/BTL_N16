Runtime libraries are declared in pom.xml and downloaded by Maven/NetBeans:
- MySQL Connector/J
- JavaFX
- Java-WebSocket
- Jackson

The server and database setup require mysql-connector-j at runtime.
Clients do not connect to MySQL and do not load this driver directly.
Use the Maven project or scripts/setup-db.cmd, scripts/run-server-dashboard.cmd and scripts/run-client.cmd.
