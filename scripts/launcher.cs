using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace GoQuizLauncher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                string appDir = AppDomain.CurrentDomain.BaseDirectory;
                Directory.SetCurrentDirectory(appDir);

                string javaExe = FindJavaExecutable(appDir);
                if (string.IsNullOrEmpty(javaExe))
                {
                    DialogResult res = MessageBox.Show(
                        "GoQuiz Adventure requires Java 17 or newer to run.\n\n" +
                        "Would you like to open the official Java download page now?\n" +
                        "(Recommended: Eclipse Adoptium / Temurin Java 17+ or Oracle Java)",
                        "GoQuiz Adventure - Java Required",
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

                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "An unexpected error occurred while launching GoQuiz Adventure:\n\n" + ex.Message +
                    "\n\nIf this persists, please reinstall or verify your Java installation.",
                    "GoQuiz Adventure - Launch Error",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        static string FindJavaExecutable(string appDir)
        {
            // 1. Check local bundled JRE / JDK folders
            string[] localPaths = new string[] {
                Path.Combine(appDir, "jre", "bin", "javaw.exe"),
                Path.Combine(appDir, "jdk", "bin", "javaw.exe"),
                Path.Combine(appDir, "..", "jre", "bin", "javaw.exe"),
                Path.Combine(appDir, "jre", "bin", "java.exe"),
                Path.Combine(appDir, "jdk", "bin", "java.exe")
            };
            foreach (string p in localPaths)
            {
                if (File.Exists(p)) return p;
            }

            // 2. Check JAVA_HOME environment variable
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string p = Path.Combine(javaHome, "bin", "javaw.exe");
                if (File.Exists(p)) return p;
                p = Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(p)) return p;
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
                        string p = Path.Combine(dir.Trim(), "javaw.exe");
                        if (File.Exists(p)) return p;
                        p = Path.Combine(dir.Trim(), "java.exe");
                        if (File.Exists(p)) return p;
                    }
                    catch { }
                }
            }

            // 4. Check standard Program Files installation paths
            string[] programRoots = new string[] {
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)
            };
            string[] vendors = new string[] { "Java", "Eclipse Adoptium", "BellSoft", "Microsoft", "Amazon Corretto", "Zulu" };

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
                                string p = Path.Combine(sub, "bin", "javaw.exe");
                                if (File.Exists(p)) return p;
                                p = Path.Combine(sub, "bin", "java.exe");
                                if (File.Exists(p)) return p;
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
