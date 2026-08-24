package org.gradle.wrapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Transparent bootstrap used because this generated archive cannot fetch Gradle's
 * official wrapper JAR while it is being assembled. It reads the standard wrapper
 * properties, downloads that pinned Gradle distribution once, and delegates to it.
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path propertiesPath = project.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }
        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null || !distributionUrl.startsWith("https://services.gradle.org/")) {
            throw new IllegalStateException("Only pinned HTTPS Gradle distributions are accepted");
        }
        String zipName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distributionName = zipName.replace("-bin.zip", "").replace("-all.zip", "");
        Path cache = Path.of(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "motionfuel", distributionName);
        Path gradleHome = cache.resolve(distributionName);
        if (!Files.isDirectory(gradleHome)) {
            Files.createDirectories(cache);
            Path archive = cache.resolve(zipName);
            Path temporary = cache.resolve(zipName + ".part");
            if (!Files.exists(archive)) {
                System.out.println("Downloading " + distributionUrl);
                try (InputStream input = URI.create(distributionUrl).toURL().openStream(); OutputStream output = Files.newOutputStream(temporary)) {
                    input.transferTo(output);
                }
                Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            unzip(archive, cache);
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path executable = gradleHome.resolve(windows ? "bin/gradle.bat" : "bin/gradle");
        if (!windows) executable.toFile().setExecutable(true);
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        for (String argument : args) command.add(argument);
        Process process = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start();
        System.exit(process.waitFor());
    }

    private static void unzip(Path archive, Path target) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = target.resolve(entry.getName()).normalize();
                if (!output.startsWith(target)) throw new SecurityException("Blocked zip path traversal");
                if (entry.isDirectory()) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
