import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';

function run(executable, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { cwd: projectRoot, stdio: 'inherit', windowsHide: true });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`Bundle command failed: ${code}`)));
  });
}

export async function packageWindowsBundle(appName, imageDirectory) {
  const framework = path.join(process.env.WINDIR ?? 'C:\\Windows', 'Microsoft.NET', 'Framework64', 'v4.0.30319');
  const compiler = path.join(framework, 'csc.exe');
  if (!existsSync(compiler)) {
    throw new Error('The Windows .NET Framework C# compiler is required for single-file bundles.');
  }
  const archive = path.join(projectRoot, 'target', `${appName}-payload.zip`);
  const script = path.join(projectRoot, 'target', `${appName}-archive.ps1`);
  const literal = value => `'${value.replaceAll("'", "''")}'`;
  writeFileSync(script, `$ErrorActionPreference = 'Stop'\nAdd-Type -AssemblyName System.IO.Compression.FileSystem\n`
    + `if (Test-Path -LiteralPath ${literal(archive)}) { Remove-Item -LiteralPath ${literal(archive)} }\n`
    + `[System.IO.Compression.ZipFile]::CreateFromDirectory(${literal(imageDirectory)}, ${literal(archive)}, [System.IO.Compression.CompressionLevel]::Optimal, $false)\n`);
  console.log(`[DIST] Building single-file ${appName}.exe...`);
  await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-File', script]);
  const version = createHash('sha256').update(readFileSync(archive)).digest('hex').slice(0, 16);
  const source = path.join(projectRoot, 'target', `${appName}-bootstrap.cs`);
  const executable = path.join(projectRoot, 'dist', `${appName}.exe`);
  writeFileSync(source, bootstrapSource(appName, version), 'utf8');
  await run(compiler, ['/nologo', '/target:winexe', '/optimize+', '/platform:x64',
    `/out:${executable}`, `/win32icon:${path.join(projectRoot, 'config', 'auction.ico')}`,
    `/resource:${archive},auction-payload.zip`,
    `/reference:${path.join(framework, 'System.IO.Compression.dll')}`,
    `/reference:${path.join(framework, 'System.IO.Compression.FileSystem.dll')}`,
    '/reference:System.Windows.Forms.dll', '/reference:System.Drawing.dll', source]);
  console.log(`[DIST] Single-file ready: ${executable}`);
}

export function bootstrapSource(appName, version) {
  if (!/^BTL16-Auction-(Server|Client)$/.test(appName) || !/^[a-f0-9]{16}$/.test(version)) {
    throw new Error('Invalid Windows bundle identity.');
  }
  return `using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

internal static class AuctionLauncher {
    private const string AppName = "${appName}";
    private const string Version = "${version}";

    [STAThread]
    private static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        using (var splash = new Form()) {
            splash.Text = "Auction";
            splash.ClientSize = new Size(410, 160);
            splash.FormBorderStyle = FormBorderStyle.None;
            splash.StartPosition = FormStartPosition.CenterScreen;
            splash.BackColor = Color.FromArgb(24, 61, 67);
            splash.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            splash.Controls.Add(new Label { Text = "AUCTION / ${appName.endsWith('Server') ? 'SERVER' : 'CLIENT'}",
                ForeColor = Color.FromArgb(190, 227, 163), Font = new Font("Segoe UI", 19, FontStyle.Bold),
                AutoSize = true, Location = new Point(30, 30) });
            splash.Controls.Add(new Label { Text = "Dang mo ung dung...", ForeColor = Color.White,
                Font = new Font("Segoe UI", 11), AutoSize = true, Location = new Point(32, 79) });
            splash.Controls.Add(new ProgressBar { Style = ProgressBarStyle.Marquee, MarqueeAnimationSpeed = 18,
                Location = new Point(32, 119), Size = new Size(345, 5) });
            splash.Shown += async (sender, args) => {
                try {
                    string directory = await Task.Run(() => Prepare());
                    Process.Start(new ProcessStartInfo(Path.Combine(directory, AppName + ".exe")) {
                        WorkingDirectory = directory, UseShellExecute = true
                    });
                } catch (Exception error) {
                    MessageBox.Show(splash, "Khong the mo Auction.\\n" + error.Message,
                        "Auction", MessageBoxButtons.OK, MessageBoxIcon.Error);
                } finally {
                    splash.Close();
                }
            };
            Application.Run(splash);
        }
    }

    private static string Prepare() {
        string directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "BTL16", "DesktopApps", AppName + "-" + Version);
        string ready = Path.Combine(directory, ".ready");
        using (var mutex = new Mutex(false, "Local\\\\BTL16-" + AppName + "-" + Version)) {
            bool acquired = false;
            try {
                try { acquired = mutex.WaitOne(TimeSpan.FromMinutes(3)); }
                catch (AbandonedMutexException) { acquired = true; }
                if (!acquired) throw new IOException("Ung dung dang duoc chuan bi. Vui long thu lai.");
                if (File.Exists(ready) && File.Exists(Path.Combine(directory, AppName + ".exe"))) return directory;
                Directory.CreateDirectory(directory);
                string root = Path.GetFullPath(directory) + Path.DirectorySeparatorChar;
                using (Stream payload = Assembly.GetExecutingAssembly().GetManifestResourceStream("auction-payload.zip"))
                using (var zip = new ZipArchive(payload, ZipArchiveMode.Read)) {
                    foreach (ZipArchiveEntry entry in zip.Entries) {
                        string destination = Path.GetFullPath(Path.Combine(directory, entry.FullName));
                        if (!destination.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                            throw new IOException("Invalid application archive path.");
                        if (String.IsNullOrEmpty(entry.Name)) {
                            Directory.CreateDirectory(destination);
                        } else {
                            Directory.CreateDirectory(Path.GetDirectoryName(destination));
                            using (Stream input = entry.Open())
                            using (Stream output = File.Create(destination)) input.CopyTo(output);
                        }
                    }
                }
                File.WriteAllText(ready, Version);
                return directory;
            } finally {
                if (acquired) mutex.ReleaseMutex();
            }
        }
    }
}
`;
}
