using System;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace GoQuizLauncher
{
    static class Program
    {
        private static int s_detectedOldVersion = 0;

        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                string appDir = AppDomain.CurrentDomain.BaseDirectory;
                Directory.SetCurrentDirectory(appDir);

                // Detect if running directly inside a temporary Windows ZIP extraction directory
                if (appDir.IndexOf(@"AppData\Local\Temp\Temp", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    appDir.IndexOf(@"AppData\Local\Temp\zip", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    MessageBox.Show(
                        "It looks like GoQuiz Adventure was started directly from inside a ZIP file without extracting.\n\n" +
                        "Windows prevents bundled runtime components from running properly inside a compressed archive.\n\n" +
                        "How to play:\n" +
                        "1. Close this window.\n" +
                        "2. Right-click the downloaded GoQuiz-Windows.zip file.\n" +
                        "3. Choose 'Extract All...' and click 'Extract'.\n" +
                        "4. Open the extracted folder and run GoQuiz.exe.\n",
                        "GoQuiz Adventure - Please Extract ZIP First",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning
                    );
                    return;
                }

                string javaExe = FindJavaExecutable(appDir);
                if (string.IsNullOrEmpty(javaExe))
                {
                    string msg;
                    if (s_detectedOldVersion > 0)
                    {
                        msg = "GoQuiz Adventure requires Java 17 or newer to run.\n\n" +
                              "Your computer is currently running Java " + s_detectedOldVersion + " (outdated).\n" +
                              "Older Java runtimes cannot launch modern applications.\n\n" +
                              "Would you like to open the official Java 17 download page now?\n" +
                              "(Recommended: Eclipse Adoptium Temurin Java 17 LTS)";
                    }
                    else
                    {
                        msg = "GoQuiz Adventure requires Java 17 or newer to run.\n\n" +
                              "No compatible Java 17+ installation was found on this computer.\n\n" +
                              "Would you like to open the official Java 17 download page now?\n" +
                              "(Recommended: Eclipse Adoptium Temurin Java 17 LTS)";
                    }

                    DialogResult res = MessageBox.Show(
                        msg,
                        "GoQuiz Adventure - Java 17+ Required",
                        MessageBoxButtons.YesNo,
                        MessageBoxIcon.Information
                    );
                    if (res == DialogResult.Yes)
                    {
                        try { Process.Start(new ProcessStartInfo("https://adoptium.net/temurin/releases/?version=17") { UseShellExecute = true }); }
                        catch { }
                    }
                    return;
                }

                // Locate the game package (check current name, standard name, or any GoQuiz jar)
                string jarPath = Path.Combine(appDir, "GoQuizAdventure.jar");
                if (!File.Exists(jarPath))
                {
                    try
                    {
                        string currentName = Path.GetFileNameWithoutExtension(AppDomain.CurrentDomain.FriendlyName);
                        string namedJar = Path.Combine(appDir, currentName + ".jar");
                        if (File.Exists(namedJar))
                        {
                            jarPath = namedJar;
                        }
                        else
                        {
                            string[] matchingJars = Directory.GetFiles(appDir, "GoQuiz*.jar");
                            if (matchingJars.Length > 0)
                            {
                                jarPath = matchingJars[0];
                            }
                        }
                    }
                    catch { }
                }

                // If not found alongside exe, extract bundled embedded JAR
                if (!File.Exists(jarPath))
                {
                    try
                    {
                        var asm = System.Reflection.Assembly.GetExecutingAssembly();
                        using (Stream stream = asm.GetManifestResourceStream("GoQuiz.jar"))
                        {
                            if (stream != null)
                            {
                                string tempDir = Path.Combine(Path.GetTempPath(), "GoQuizAdventure");
                                Directory.CreateDirectory(tempDir);
                                string tempJar = Path.Combine(tempDir, "GoQuizAdventure.jar");
                                using (FileStream fs = new FileStream(tempJar, FileMode.Create, FileAccess.Write))
                                {
                                    byte[] buffer = new byte[81920];
                                    int read;
                                    while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                                    {
                                        fs.Write(buffer, 0, read);
                                    }
                                }
                                jarPath = tempJar;
                            }
                        }
                    }
                    catch { }
                }

                string launchArgs;
                if (File.Exists(jarPath))
                {
                    launchArgs = "-Dfile.encoding=UTF-8 -jar \"" + jarPath + "\"";
                }
                else if (Directory.Exists(Path.Combine(appDir, "desktop")))
                {
                    launchArgs = "-Dfile.encoding=UTF-8 -cp \"desktop;app;.\" QuizGame";
                }
                else
                {
                    MessageBox.Show(
                        "Could not locate the GoQuiz game files (GoQuizAdventure.jar).\n\n" +
                        "Please make sure the .exe application and the .jar file are in the same folder.",
                        "GoQuiz Adventure - Missing Game Files",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning
                    );
                    return;
                }

                if (args != null && args.Length > 0)
                {
                    launchArgs += " " + string.Join(" ", args);
                }

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = javaExe;
                psi.Arguments = launchArgs;
                psi.WorkingDirectory = appDir;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;

                Process gameProcess = Process.Start(psi);
                if (gameProcess != null)
                {
                    // If the game crashes on startup (within 700ms), detect it and notify the user
                    if (gameProcess.WaitForExit(700) && gameProcess.ExitCode != 0)
                    {
                        MessageBox.Show(
                            "The game process closed unexpectedly (Exit Code: " + gameProcess.ExitCode + ").\n\n" +
                            "Please ensure all files from the ZIP archive were extracted properly.\n" +
                            "If the issue persists, please install Java 17 LTS.",
                            "GoQuiz Adventure - Launch Error",
                            MessageBoxButtons.OK,
                            MessageBoxIcon.Error
                        );
                    }
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "An unexpected error occurred while launching GoQuiz Adventure:\n\n" + ex.Message +
                    "\n\nIf this persists, please ensure Java 17 or newer is installed.",
                    "GoQuiz Adventure - Launch Error",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        static int GetJavaMajorVersion(string javaExe)
        {
            try
            {
                // When probing version, use java.exe if available in the same directory because
                // javaw.exe on some Windows configurations does not attach or stream to stderr.
                string probeExe = javaExe;
                try
                {
                    string dir = Path.GetDirectoryName(javaExe);
                    if (!string.IsNullOrEmpty(dir))
                    {
                        string sisterJava = Path.Combine(dir, "java.exe");
                        if (File.Exists(sisterJava)) probeExe = sisterJava;
                    }
                }
                catch { }

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = probeExe;
                psi.Arguments = "-version";
                psi.UseShellExecute = false;
                psi.RedirectStandardError = true;
                psi.RedirectStandardOutput = true;
                psi.CreateNoWindow = true;

                using (Process p = Process.Start(psi))
                {
                    string output = p.StandardError.ReadToEnd() + " " + p.StandardOutput.ReadToEnd();
                    p.WaitForExit(3000);

                    // Formats:
                    // "openjdk version \"17.0.2\"" -> 17
                    // "java version \"1.8.0_361\"" -> 8
                    // "java version \"21.0.1\""    -> 21
                    var match = Regex.Match(output, @"version\s+""(\d+)(\.(\d+))?");
                    if (match.Success)
                    {
                        int major = int.Parse(match.Groups[1].Value);
                        if (major == 1 && match.Groups[3].Success)
                        {
                            major = int.Parse(match.Groups[3].Value);
                        }
                        return major;
                    }
                }
            }
            catch { }
            return 0;
        }

        static string CheckCandidate(string candidate)
        {
            if (string.IsNullOrEmpty(candidate) || !File.Exists(candidate)) return null;

            int ver = GetJavaMajorVersion(candidate);
            if (ver >= 17)
            {
                return candidate;
            }
            if (ver > 0 && s_detectedOldVersion == 0)
            {
                s_detectedOldVersion = ver;
            }
            return null;
        }

        static string FindJavaExecutable(string appDir)
        {
            // 1. Check local bundled JRE / JDK folders (Priority #1 - Portable Zero-Install)
            string[] localPaths = new string[] {
                Path.Combine(appDir, "jre", "bin", "javaw.exe"),
                Path.Combine(appDir, "jdk", "bin", "javaw.exe"),
                Path.Combine(appDir, "..", "jre", "bin", "javaw.exe"),
                Path.Combine(appDir, "jre", "bin", "java.exe"),
                Path.Combine(appDir, "jdk", "bin", "java.exe")
            };
            foreach (string p in localPaths)
            {
                if (File.Exists(p))
                {
                    int ver = GetJavaMajorVersion(p);
                    if (ver >= 17) return p;
                }
            }

            // 2. Check JAVA_HOME environment variable
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string res = CheckCandidate(Path.Combine(javaHome, "bin", "javaw.exe"));
                if (res != null) return res;
                res = CheckCandidate(Path.Combine(javaHome, "bin", "java.exe"));
                if (res != null) return res;
            }

            // 3. Check System PATH
            string pathEnv = Environment.GetEnvironmentVariable("PATH");
            if (!string.IsNullOrEmpty(pathEnv))
            {
                string[] dirs = pathEnv.Split(';');
                foreach (string dir in dirs)
                {
                    try
                    {
                        string res = CheckCandidate(Path.Combine(dir.Trim(), "javaw.exe"));
                        if (res != null) return res;
                        res = CheckCandidate(Path.Combine(dir.Trim(), "java.exe"));
                        if (res != null) return res;
                    }
                    catch { }
                }
            }

            // 4. Check standard Program Files installation paths
            string[] programRoots = new string[] {
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)
            };
            string[] vendors = new string[] { "Eclipse Adoptium", "BellSoft", "Microsoft", "Amazon Corretto", "Zulu", "Java" };

            foreach (string root in programRoots)
            {
                if (string.IsNullOrEmpty(root) || !Directory.Exists(root)) continue;
                foreach (string vendor in vendors)
                {
                    string vendorDir = Path.Combine(root, vendor);
                    if (Directory.Exists(vendorDir))
                    {
                        try
                        {
                            string[] subdirs = Directory.GetDirectories(vendorDir);
                            Array.Reverse(subdirs); // Check newer versions first
                            foreach (string sub in subdirs)
                            {
                                string res = CheckCandidate(Path.Combine(sub, "bin", "javaw.exe"));
                                if (res != null) return res;
                                res = CheckCandidate(Path.Combine(sub, "bin", "java.exe"));
                                if (res != null) return res;
                            }
                        }
                        catch { }
                    }
                }
            }

            return null;
        }
    }
}
