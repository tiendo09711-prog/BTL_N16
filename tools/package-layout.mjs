import path from 'node:path';

export const mainJar = 'btl16-realtime-auction-1.0.0.jar';

export function applicationDetails(role) {
  if (!['server', 'client'].includes(role)) {
    throw new Error('Application role must be server or client.');
  }
  return {
    appName: `BTL16-Auction-${role === 'server' ? 'Server' : 'Client'}`,
    mainClass: role === 'server'
      ? 'vn.ptit.btl16.server.dashboard.ServerDashboardMain'
      : 'vn.ptit.btl16.client.ClientMain'
  };
}

export function desktopLayout(role, platform, architecture) {
  const details = applicationDetails(role);
  if (platform === 'win32') {
    return { ...details, destination: 'dist', imageName: details.appName,
      contentDirectory: 'app', jpackageBinary: 'jpackage.exe' };
  }
  if (platform !== 'darwin' || !['arm64', 'x64'].includes(architecture)) {
    throw new Error('Native desktop packaging supports Windows and macOS (arm64/x64) only.');
  }
  return { ...details, destination: path.join('dist', `macos-${architecture}`),
    imageName: `${details.appName}.app`, contentDirectory: path.join('Contents', 'app'),
    jpackageBinary: 'jpackage' };
}

export function verifyJavafxLibraries(files, classifier) {
  for (const module of ['base', 'graphics', 'controls']) {
    if (!files.some(file => file.startsWith(`javafx-${module}-`) && file.endsWith(`-${classifier}.jar`))) {
      throw new Error(`Missing JavaFX ${module} native library for ${classifier}`);
    }
  }
  const foreign = files.filter(file => /^javafx-.*-(win|linux|mac)(-[\w-]+)?\.jar$/.test(file)
    && !file.endsWith(`-${classifier}.jar`));
  if (foreign.length) {
    throw new Error(`Mixed JavaFX platforms in package: ${foreign.join(', ')}`);
  }
}

export function portableLauncher(role, architecture) {
  const { mainClass } = applicationDetails(role);
  if (!['arm64', 'x64'].includes(architecture)) {
    throw new Error('macOS architecture must be arm64 or x64.');
  }
  const javaOptions = role === 'server'
    ? '"-Dbtl16.server.config=$PWD/config/server.properties"'
    : '-Dbtl16.client.transport=websocket -Dbtl16.client.manualConnect=true';
  const acceptedArch = architecture === 'arm64' ? 'aarch64|arm64' : 'x86_64|amd64';
  return `#!/bin/bash
set -eu
cd "$(dirname "$0")"
fail() {
  printf '\\n%s\\n' "$1"
  if [ -t 0 ]; then read -r -p "Press Enter to close..." answer; fi
  exit 1
}
[ "$(uname -s)" = "Darwin" ] || fail "This package is for macOS only."
if [ -n "\${JAVA_HOME:-}" ]; then
  java_bin="$JAVA_HOME/bin/java"
else
  java_home=$(/usr/libexec/java_home -v '17+' 2>/dev/null) || fail "Install a macOS ${architecture} JDK 17 or newer, then try again."
  java_bin="$java_home/bin/java"
fi
[ -x "$java_bin" ] || fail "JAVA_HOME does not contain an executable bin/java."
settings=$("$java_bin" -XshowSettings:properties -version 2>&1) || fail "Java could not start."
java_version=$(printf '%s\\n' "$settings" | awk '$1 == "java.specification.version" {print $3}')
case "$java_version" in ''|*[!0-9]*) fail "Java 17 or newer is required." ;; esac
[ "$java_version" -ge 17 ] || fail "Java 17 or newer is required."
java_arch=$(printf '%s\\n' "$settings" | awk '$1 == "os.arch" {print $3}')
case "$java_arch" in ${acceptedArch}) ;; *) fail "This package requires a ${architecture} Java runtime. Use the matching package/JDK." ;; esac
"$java_bin" -Dfile.encoding=UTF-8 ${javaOptions} -cp 'app/*' ${mainClass} || fail "Application exited with an error. See the message above."
`;
}
