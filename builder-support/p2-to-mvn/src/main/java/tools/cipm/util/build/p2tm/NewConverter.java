package tools.cipm.util.build.p2tm;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.jar.JarFile;

import org.openntf.maven.p2.model.P2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewConverter {
	private static final String REQUIRE_BUNDLE = "Require-Bundle";
	
    private static final Logger logger = LoggerFactory.getLogger(NewConverter.class);
    private static final String JAR_FILE_EXTENSION = ".jar";
    private static final String URI_FILE_PREFIX = "file://";
    private static final String URI_HTTP_SCHEME = "http";
    private static final String URI_HTTPS_SCHEME = "https";

    /**
     * Maps a bundle symbolic name to the Maven groupId under which it
     * is (or will be) installed into the ../mvn repository.
     *
     * This is needed because several bundles that depend on each other
     * may live under different groupIds. Populate this map for the
     * bundles you care about; entries that are absent fall back to
     * the default group id passed to the installer.
     */
    private static Map<String, String> ARTIFACT_ID_TO_GROUP_ID = new LinkedHashMap<>();
    static {
        // Example entries. Adjust to match the actual groupIds in ../mvn.
        // BUNDLE_TO_GROUP.put("org.palladiosimulator.pcm", "org.palladiosimulator.core");
        // BUNDLE_TO_GROUP.put("de.uka.ipd.sdq.stoex", "org.palladiosimulator.commons");
        // BUNDLE_TO_GROUP.put("org.eclipse.emf.ecore", "org.eclipse.platform");
    }

    private static Map<String, String> buildArtifactToGroupIndex(Path repoRoot) {
        Map<String, String> index = new LinkedHashMap<>();
        // repoRoot = "mvn" (relative to where the converter runs)
        int repoRootDepth = repoRoot.getNameCount();

        try (var stream = Files.walk(repoRoot)) {
            stream
                .filter(Files::isDirectory)
                .filter(p -> p.getNameCount() > repoRootDepth + 1) // groupId + artifactId
                .filter(p -> p.toFile().listFiles(f -> f.getName().equals("maven-metadata-local.xml")).length != 0)
                .forEach(dir -> {
                    // artifactId is the last segment; the parent path is the groupId
                    String artifactId = dir.getFileName().toString();
                    String groupId = repoRoot.relativize(dir.getParent())
                        .toString().replace('/', '.');
                    index.putIfAbsent(artifactId, groupId);
                });
        } catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
        return index;
    }
    
    public static Map<String, String> buildArtifactToGroupIndexCache() {
    	var mvnDirPath = new File("../../mvn").toPath();
    	ARTIFACT_ID_TO_GROUP_ID = buildArtifactToGroupIndex(mvnDirPath);
    	return ARTIFACT_ID_TO_GROUP_ID;
    }
    
    /**
     * A minimal Maven dependency coordinate resolved for a required bundle.
     */
    private static final class Dependency {
        final String groupId;
        final String artifactId;
        final String version;
        Dependency(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
    }

    public static void main(String[] args) {
        // installJarsFromLocalDirectory();
        // installJarsFromRemoteRepository();
        // installJarsFromRepository("", URI_FILE_PREFIX + "", true);
    }

    // ---------- unchanged: installJarsFromLocalDirectory() ----------
    public static void installJarsFromLocalDirectory() {
        var consideredPath = Paths.get("target", "jars");
        if (Files.notExists(consideredPath)) {
            System.out.println("Cannot consider the directory. It does not exist.");
            return;
        }

        try (var scanner = new Scanner(System.in)) {
            var lastGroupIdContainer = new StringBuilder();
            Files
                .walk(consideredPath)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    var fileName = path.getFileName().toString();
                    if (!fileName.endsWith(JAR_FILE_EXTENSION)) {
                        return;
                    }

                    var fileNameParts = fileName.split("_");

                    System.out.println("You need to specify a group ID for the artifact "
                        + fileName + ". Please enter it. Leave the ID empty if the last group ID "
                        + lastGroupIdContainer + " should be reused.");
                    var potentialGroupId = scanner.next();
                    if (!potentialGroupId.isBlank()) {
                        lastGroupIdContainer.setLength(0);
                        lastGroupIdContainer.append(potentialGroupId);
                    }

                    try {
                        installJarLocally(
                            path.toString(),
                            lastGroupIdContainer.toString(),
                            fileNameParts[0],
                            fileNameParts[1].substring(0,
                                fileNameParts[1].length() - JAR_FILE_EXTENSION.length())
                        );
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Could not process " + fileName);
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------- unchanged: installJarsFromRemoteRepository() ----------
    public static void installJarsFromRemoteRepository() {
        String id = System.getProperty("p2tm.id");
        String repoUri = System.getProperty("p2tm.uri");
        if (id == null || repoUri == null) {
            System.out.println("No Id or URI given. Stopping.");
            return;
        }

        installJarsFromRepository(id, repoUri, false);
    }

    // ---------- unchanged: installJarsFromRepository() ----------
    public static void installJarsFromRepository(String id, String repoUri, boolean isLocal) {
        P2Repository p2Repo = P2Repository.getInstance(URI.create(repoUri), logger);
        var allBundles = p2Repo.getBundles();

        HttpClient client = HttpClient.newHttpClient();
        for (var bundle : allBundles) {
            System.out.println("Downloading " + bundle.getId());

            try {
                if (isLocal) {
                    var bundleUri = bundle.getUri("");
                    if (!URI_HTTP_SCHEME.equals(bundleUri.getScheme())
                            && !URI_HTTPS_SCHEME.equals(bundleUri.getScheme())
                            && Files.exists(Paths.get(bundleUri))) {
                        installJarLocally(bundleUri.getSchemeSpecificPart(), id,
                            bundle.getId(), bundle.getVersion());
                    }
                    continue;
                }

                var tempStorageFile = Paths.get("target",
                    bundle.getId() + "_" + bundle.getVersion() + ".jar");

                if (Files.notExists(tempStorageFile)) {
                    var bodyHandler = BodyHandlers.ofFile(tempStorageFile);
                    client.send(
                        HttpRequest.newBuilder(bundle.getUri("")).GET().build(),
                        bodyHandler
                    );
                }

                installJarLocally(tempStorageFile.toString(), id, bundle.getId(), bundle.getVersion());

                Files.delete(tempStorageFile);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Installs a jar into the local Maven repository, generating and attaching
     * a proper POM that lists the bundle's transitive dependencies.
     */
    public static void installJarLocally(String filePath, String groupId,
            String artifactId, String version) throws IOException, InterruptedException {
        // Build the dependency list from the bundle's own manifest.
        List<Dependency> dependencies = readRequiredBundles(filePath, groupId);

        // Serialize a POM that declares those dependencies.
        Path pomFile = buildPomFile(groupId, artifactId, version, dependencies);

        try {
            var subProcess = new ProcessBuilder(
                "./mvnw", "install:install-file", "-DlocalRepositoryPath=../mvn",
                "-Dfile=" + filePath,
                "-DgroupId=" + groupId,
                "-DartifactId=" + artifactId,
                "-Dversion=" + version,
                "-Dpackaging=jar", "-DcreateChecksum=true",
                "-DpomFile=" + pomFile)
                .inheritIO()
                .start();
            var subProcessResult = subProcess.waitFor();
            System.out.println(subProcessResult);
        } finally {
            Files.deleteIfExists(pomFile);
        }
    }

    /**
     * Reads META-INF/MANIFEST.MF from the given jar and returns a list of
     * dependencies derived from the {@code Require-Bundle} header.
     *
     * @param jarPath      path to the bundle jar
     * @param defaultGroup group id used when a required bundle has no entry
     *                     in {@link #ARTIFACT_ID_TO_GROUP_ID}
     */
    public static List<Dependency> readRequiredBundles(String jarPath, String defaultGroup)
            throws IOException {
        Path resolved = Paths.get(jarPath);
        if (!Files.exists(resolved)) {
            return Collections.emptyList();
        }

        List<Dependency> dependencies = new ArrayList<>();
        try (JarFile jar = new JarFile(resolved.toFile())) {
            var manifest = jar.getManifest();
            if (manifest == null) {
                return Collections.emptyList();
            }

            String requireBundle = manifest.getMainAttributes()
                .getValue(REQUIRE_BUNDLE); // "Require-Bundle"
            if (requireBundle == null || requireBundle.isBlank()) {
                return Collections.emptyList();
            }

            for (String entry : requireBundle.split(",")) {
                String clean = entry.trim();
                if (clean.isEmpty()) {
                    continue;
                }
                // A Require-Bundle entry may carry attributes/version constraints.
                // The symbolic name precedes the first ';'.
                String name = clean.substring(0, clean.indexOf(';') == -1
                    ? clean.length() : clean.indexOf(';')).trim();

                String depGroup = ARTIFACT_ID_TO_GROUP_ID.getOrDefault(name, defaultGroup);
                // NOTE: version is taken from the version constraint's upper
                // bound when available, otherwise the default group's guess.
                String version = extractVersion(clean, defaultGroup);
                dependencies.add(new Dependency(depGroup, name, version));
            }
        }
        return dependencies;
    }

    /**
     * Best-effort extraction of a concrete Maven version from a Require-Bundle
     * constraint such as {@code "de.uka.ipd.sdq.stoex;bundle-version=\"[5.0.0,6.0.0)\""}.
     * Falls back to the bundle's group id (a poor proxy) when nothing usable is found.
     */
    public static String extractVersion(String requireBundleEntry, String fallback) {
        int idx = requireBundleEntry.indexOf("bundle-version");
        if (idx == -1) {
            return fallback;
        }
        int open = requireBundleEntry.indexOf('"', idx);
        int close = requireBundleEntry.indexOf('"', open + 1);
        if (open == -1 || close == -1) {
            return fallback;
        }
        String range = requireBundleEntry.substring(open + 1, close);
        // Range "x" or "[x,y)" or "[x,y]". Take the first version token.
        String trimmed = range.replace("[", "").replace("(", "").replace("]", "").replace(")", "");
        int comma = trimmed.indexOf(',');
        String first = (comma == -1) ? trimmed : trimmed.substring(0, comma);
        return first.isBlank() ? fallback : first.trim();
    }

    /**
     * Serializes a minimal Maven POM that declares the given dependencies,
     * writes it to a temp file in target/, and returns its path.
     */
    public static Path buildPomFile(String groupId, String artifactId, String version,
            List<Dependency> dependencies) throws IOException {
        String xml = renderPom(groupId, artifactId, version, dependencies);

        Path targetDir = Paths.get("target");
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path pomFile = targetDir.resolve(artifactId + "_" + version + "__pom.xml");
        Files.writeString(pomFile, xml);
        return pomFile;
    }

    public static String renderPom(String groupId, String artifactId, String version,
            List<Dependency> dependencies) {
        StringBuilder deps = new StringBuilder();
        if (dependencies != null) {
            for (Dependency d : dependencies) {
                deps.append("    <dependency>\n")
                    .append("      <groupId>").append(xmlEscape(d.groupId)).append("</groupId>\n")
                    .append("      <artifactId>").append(xmlEscape(d.artifactId)).append("</artifactId>\n")
                    .append("      <version>").append(xmlEscape(d.version)).append("</version>\n")
                    .append("    </dependency>\n");
            }
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
            + "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>" + xmlEscape(groupId) + "</groupId>\n"
            + "  <artifactId>" + xmlEscape(artifactId) + "</artifactId>\n"
            + "  <version>" + xmlEscape(version) + "</version>\n"
            + "  <packaging>jar</packaging>\n"
            + "  <dependencies>\n" + deps + "  </dependencies>\n"
            + "</project>\n";
    }

    public static String xmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}