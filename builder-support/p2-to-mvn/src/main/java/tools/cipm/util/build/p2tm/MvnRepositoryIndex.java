package tools.cipm.util.build.p2tm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Builds a flat, complete index of the ../mvn repository as a single list
 * of {@link Coordinate}s. Because every coordinate is self-describing, there
 * is no need for multiple overlapping index maps.
 */
public final class MvnRepositoryIndex {

    private MvnRepositoryIndex() {}

    /**
     * Builds one {@link Coordinate} per installed bundle in the repository.
     *
     * <p>Each Maven artifact is stored at:
     * <pre>  &lt;repoRoot&gt;/&lt;groupPath&gt;/&lt;artifactId&gt;/&lt;version&gt;/&lt;artifactId&gt;-&lt;version&gt;.jar</pre>
     *
     * A single walk of the repo derives every field from that layout plus the
     * jar's OSGi {@code Bundle-SymbolicName}.
     *
     * @param repoRoot the repository root (e.g. {@code Paths.get("../mvn")})
     * @return the list of coordinates for every installed bundle
     * @throws IOException if the repository cannot be walked
     */
    public static List<Coordinate> build(Path repoRoot) {
        List<Coordinate> coordinates = new ArrayList<>();
        int repoRootDepth = repoRoot.getNameCount();

        try (var stream = Files.walk(repoRoot)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .forEach(jarPath -> {
                    Coordinate coord = coordinateFor(jarPath, repoRoot, repoRootDepth);
                    if (coord != null) {
                        coordinates.add(coord);
                    }
                });
        } catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}

        return coordinates;
    }

    /**
     * Derives groupId, artifactId, version and bundle name from a single jar's
     * repository path and manifest.
     */
    private static Coordinate coordinateFor(Path jarPath, Path repoRoot, int repoRootDepth) {
        Path artifactDir = jarPath.getParent();          // .../<groupId>/<artifactId>/<version>
        if (artifactDir == null) {
            return null;
        }
        String versionDir = artifactDir.getFileName().toString();
        Path artifactIdDir = artifactDir.getParent();    // .../<groupId>/<artifactId>
        Path groupPath = artifactIdDir == null ? null : artifactIdDir.getParent(); // .../<groupId>

        if (artifactIdDir == null || groupPath == null || groupPath.getNameCount() < repoRootDepth) {
            return null; // malformed layout, skip
        }

        String artifactId = artifactIdDir.getFileName().toString();
        String groupId = repoRoot.relativize(groupPath).toString().replace('/', '.');

        // The filename's version is more reliable than trusting the directory name.
        String fileName = jarPath.getFileName().toString(); // <artifactId>-<version>.jar
        String version = versionDir;
        if (fileName.startsWith(artifactId + "-") && fileName.endsWith(".jar")) {
            version = fileName.substring(artifactId.length() + 1,
                fileName.length() - ".jar".length());
        }

        String bundleName = readBundleSymbolicName(jarPath).orElse(artifactId);

        return new Coordinate(groupId, artifactId, version, bundleName);
    }

    /**
     * Reads the OSGi Bundle-SymbolicName from a jar's manifest, dropping any
     * parameters after ';'.
     */
    private static Optional<String> readBundleSymbolicName(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var manifest = jar.getManifest();
            if (manifest == null) {
                return Optional.empty();
            }
            String bsn = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
            if (bsn == null || bsn.isBlank()) {
                return Optional.empty();
            }
            int semi = bsn.indexOf(';');
            return Optional.of((semi == -1) ? bsn : bsn.substring(0, semi));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}