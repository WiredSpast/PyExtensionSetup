import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.scene.control.*;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class PyExtensionSetup {

    private static final String[] PORT_FLAG = {"--port", "-p"};
    private static final String[] FILE_FLAG = {"--filename", "-f"};
    private static final String[] COOKIE_FLAG = {"--auth-token", "-c"};
    private static final String[] MIN_VERSION_FLAG = {"--min-version", "-v"};
    private static final String[] EXTENSION_FLAG = {"--extension", "-e"};

    public static void main(String[] args) {
        runSetup(args);
    }

    private static String getArgument(String[] args, String... arg) {
        for (int i = 0; i < args.length - 1; i++) {
            for (String str : arg) {
                if (args[i].equalsIgnoreCase(str)) {
                    return args[i+1];
                }
            }
        }
        return null;
    }

    private static void runSetup(String[] args) {
        try {
            String extensionScript = getArgument(args, EXTENSION_FLAG);
            if (extensionScript == null) throw new Exception("Python extension file must be defined in the run args (-e script.py)");
            String minVersion = getArgument(args, MIN_VERSION_FLAG);
            if (minVersion == null) throw new Exception("Minimum python version has to be defined in the run args (-v 3.2.0)");
            String port = getArgument(args, PORT_FLAG);
            if (port == null) throw new Exception("G-Earth extension port must be defined in run args (-p 9092)");

            if(!isPythonInstalledAndUpToDate(minVersion)) installPython(minVersion);

            fixRequirements();
            System.out.println("Requirements fixed");

            runExtension(args);
            System.out.println("extension ran");
            clearCache();
            System.out.println("cache cleared");
        } catch (Exception e) {
            new JFXPanel(); // Create JavaFX thread
            Platform.runLater(() -> {
                try {
                    new File("error.txt");
                    FileOutputStream fos = new FileOutputStream(new File("error.txt"));
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error while setting up python extension!");
                    error.setHeaderText("Error in setup of " + new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile().getParentFile().getName());
                    e.printStackTrace(new PrintStream(fos));
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                } catch (IOException ignored) {}
            });
        }
    }

    private static boolean isPythonInstalledAndUpToDate(String minVersion) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "--version");
            Process p = pb.start();

            String error = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8)).readLine();
            if(error != null) return false;
            String version = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)).readLine();
            version = version.replace("Python ", "");

            return isNewerOrEqual(version, minVersion);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isNewerOrEqual(String actualVersion, String minVersion) {
        String[] actualSplit = actualVersion.trim().split("\\.");
        String[] minSplit = minVersion.trim().split("\\.");

        for(int i = 0; i < actualSplit.length && i < minSplit.length; i++) {
            int vDif = Integer.parseInt(actualSplit[i]) - Integer.parseInt(minSplit[i]);
            if(vDif > 0) return true;
            if(vDif < 0) return false;
        }

        return true;
    }

    private static void installPython(String minVersion) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("win")) {
            installOnWindows(minVersion);
        } else if(os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            installOnLinux(minVersion);
        } else if(os.contains("mac")) {
            installOnMac(minVersion);
        } else {
            throw new Exception("Unsupported Operating System");
        }
    }

    private static boolean requestContinueApproval(String title, String header, String content) throws ExecutionException, InterruptedException {
        final FutureTask<Boolean> requestTask = new FutureTask<>(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            AtomicBoolean result = new AtomicBoolean(false);
            final Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(ActionEvent.ACTION, event -> result.set(true));
            final Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
            cancel.addEventFilter(ActionEvent.ACTION, event -> result.set(false));

            alert.showAndWait();

            return result.get();
        });

        new JFXPanel(); // Create JavaFX thread
        Platform.runLater(requestTask);

        return requestTask.get();
    }

    private static void installOnWindows(String minVersion) throws Exception {
        if(!requestContinueApproval(String.format("Install/Update Python %s", minVersion),
                "Python not found or newer version required",
                "Do you want to install/update Python?\nExtension will be launched once installation is completed")) throw new Exception("Python installation rejected, extension wont be able to run!");
        // Alternative: Install Chocolatey or Nuget (Windows package manager)
        try {
            String downloadUrl = String.format("https://www.python.org/ftp/python/%s/python-%s-amd64.exe", minVersion, minVersion);
            File installerExe = downloadCacheFile(new URL(downloadUrl), "pythonInstaller.exe");
            ProcessBuilder pb = new ProcessBuilder(installerExe.toString(), "/quiet", "PrependPath=1");
            pb.directory(new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
            Process p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            throw new Exception("Error while installing Python");
        }
    }

    private static void installOnLinux(String minVersion) throws Exception {
        // Too complicated to include all distributions
        try {
            if(!requestContinueApproval(String.format("Install/Update Python %s", minVersion),
                    new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile().getParentFile().getName() + " requires Python to be installed/updated!",
                    String.format("Use your local package manager to update python to a version of %s or higher, afterwards click OK\n(If you click OK before installing/updating, the extension will not run this time)", minVersion))){
                throw new Exception("Setup cancelled, extension will most likely not launch!");
            }
        } catch (Exception e) {
            throw new Exception("Error while requesting Python installation");
        }
    }

    private static void installOnMac(String minVersion) throws Exception {
        if(!requestContinueApproval(String.format("Install/Update Python %s", minVersion),
                "Python not found or newer version required",
                "Do you want to install/update Python?\nExtension will be launched once installation is completed")) throw new Exception("Python installation rejected, extension wont be able to run!");
        // Alternative: Install Brew (Mac/Linux package manager)
        try {
            String downloadUrl = String.format("https://www.python.org/ftp/python/%s/python-%s-macosx10.6.pkg", minVersion, minVersion);
            File installerPkg = downloadCacheFile(new URL(downloadUrl), "pythonInstaller.pkg");
            ProcessBuilder pb = new ProcessBuilder("installer", "-pkg", installerPkg.toString(), "-target", "CurrentUserHomeDirectory");
            pb.directory(new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
            Process p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            throw new Exception("Error while installing Python");
        }
    }

    private static File downloadCacheFile(URL url, String fileName) throws IOException {
        String cachePath = "setupCache/";
        Files.createDirectories(Paths.get(cachePath));
        File file = new File(cachePath + fileName);
        InputStream is = url.openStream();
        FileOutputStream fos = new FileOutputStream(file);

        int length = -1;
        byte[] buffer = new byte[1024];
        while((length = is.read(buffer)) > -1) {
            fos.write(buffer, 0, length);
        }
        fos.close();
        is.close();
        return file;
    }

    private static void clearCache() {
        File cacheFolder = new File("setupCache");
        if(cacheFolder.exists()) {
            emptyDirectory(cacheFolder);
        }
        cacheFolder.delete();
    }

    private static void emptyDirectory(File dir) {
        File[] files = dir.listFiles();
        if(files != null) {
            for (File file : files) {
                if(file.isDirectory()) emptyDirectory(file);
                file.delete();
            }
        }
    }

    private static void fixRequirements() throws Exception {
        try {
            File req = new File("requirements.txt");
            if (req.exists()) {
                ProcessBuilder pb = new ProcessBuilder("pip", "install", "-r", "requirements.txt");
                pb.directory(new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
                Process p = pb.start();
                p.waitFor();
            }
        } catch (Exception e) {
            throw new Exception("Error while installing requirements, extension might not work!");
        }
    }

    private static void runExtension(String[] args) throws Exception {
        String extensionScript = getArgument(args, EXTENSION_FLAG);
        String cookie = getArgument(args, COOKIE_FLAG);
        String file = getArgument(args, FILE_FLAG);
        String port = getArgument(args, PORT_FLAG);

        ArrayList<String> command =  new ArrayList<>(Arrays.asList("python", extensionScript, "-p", port));
        if(file != null) command.addAll(Arrays.asList("-f", file));
        if(cookie != null) command.addAll(Arrays.asList("-c", cookie));

        ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]));
        pb.directory(new File(URLDecoder.decode(PyExtensionSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF8")).getParentFile());
        pb.start();
    }
}
