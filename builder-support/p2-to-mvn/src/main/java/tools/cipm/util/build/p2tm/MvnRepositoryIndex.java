package tools.cipm.util.build.p2tm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Builds a flat, complete index of the ../mvn repository as a single list of
 * {@link Coordinate}s. Because every coordinate is self-describing, there is no
 * need for multiple overlapping index maps.
 */
public final class MvnRepositoryIndex {
	private static final String EXPORT_PACKAGE = "Export-Package";
	private static final String REQUIRE_BUNDLE = "Require-Bundle";
	private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
	private static final String JAR_EXTENSION = ".jar";

	/**
	 * Only for artifacts whose "bundle name" is a repository convention and not
	 * discoverable from the artifact itself (e.g. plain non-OSGi jars that have no
	 * Bundle-SymbolicName in the manifest). Keyed by Maven artifactId.
	 */
	private static final Map<String, String> ARTIFACT_TO_BUNDLE_NAME = new LinkedHashMap<>();
	static {
		ARTIFACT_TO_BUNDLE_NAME.put("api", "org.pcm.headless.api");
	}

	/**
	 * Bundles provided by the runtime / target platform that need no Maven
	 * declaration. Curate per deployment (e.g. the OSGi framework).
	 */
	private static final Set<String> PROVIDED_BUNDLES = new HashSet<>();
	static {
		PROVIDED_BUNDLES.add("org.eclipse.osgi");
		PROVIDED_BUNDLES.add("org.osgi.framework");
	}

	private static Set<Coordinate> coordList;

	private MvnRepositoryIndex() {
	}

	public static Collection<Coordinate> getMvnRepositoryIndices() {
		return coordList;
	}

	/**
	 * Builds one {@link Coordinate} per installed bundle in the repository.
	 *
	 * <p>
	 * Each Maven artifact is stored at:
	 * 
	 * <pre>
	 *   &lt;repoRoot&gt;/&lt;groupPath&gt;/&lt;artifactId&gt;/&lt;version&gt;/&lt;artifactId&gt;-&lt;version&gt;.jar
	 * </pre>
	 *
	 * A single walk of the repo derives every field from that layout plus the jar's
	 * OSGi {@code Bundle-SymbolicName}.
	 *
	 * @param repoRoot the repository root (e.g. {@code Paths.get("../mvn")})
	 * @return the list of coordinates for every installed bundle
	 * @throws IOException if the repository cannot be walked
	 */
	public static Collection<Coordinate> build(Path repoRoot) {
		var coordinates = new HashSet<Coordinate>();
		int repoRootDepth = repoRoot.getNameCount();

		try (var stream = Files.walk(repoRoot)) {
			stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(JAR_EXTENSION))
					.forEach(jarPath -> coordinates.addAll(coordinatesFor(jarPath, repoRoot, repoRootDepth)));
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}

		coordList = coordinates;
		return coordinates;
	}

	/**
	 * Derives groupId, artifactId, version and bundle name from a single jar's
	 * repository path and manifest.
	 */
	private static List<Coordinate> coordinatesFor(Path jarPath, Path repoRoot, int repoRootDepth) {
		// --- derive the shared Maven identity (unchanged from before) ---
		Path artifactDir = jarPath.getParent();
		if (artifactDir == null)
			return List.of();

		String versionDir = artifactDir.getFileName().toString();
		Path artifactIdDir = artifactDir.getParent();
		Path groupPath = artifactIdDir == null ? null : artifactIdDir.getParent();
		if (artifactIdDir == null || groupPath == null || groupPath.getNameCount() < repoRootDepth) {
			return List.of();
		}

		String artifactId = artifactIdDir.getFileName().toString();
		String groupId = repoRoot.relativize(groupPath).toString().replace('/', '.');

		String fileName = jarPath.getFileName().toString();
		String version = versionDir;
		if (fileName.startsWith(artifactId + "-") && fileName.endsWith(".jar")) {
			version = fileName.substring(artifactId.length() + 1, fileName.length() - ".jar".length());
		}

		// --- bundle name (manifest, or curated override, or artifactId fallback) ---
		String bundleName = readBundleSymbolicName(jarPath)
				.orElseGet(() -> ARTIFACT_TO_BUNDLE_NAME.getOrDefault(artifactId, artifactId));

		// --- exported packages: one Coordinate per package ---
		List<String> packages = readExportedPackages(jarPath);
		if (packages.isEmpty()) {
			// No Export-Package: fall back to a single Coordinate keyed by
			// the bundle name / artifactId, so lookups by bundle still work.
			return List.of(new Coordinate(groupId, artifactId, version, bundleName, bundleName));
		}

		List<Coordinate> result = new ArrayList<>();
		for (String pkg : packages) {
			result.add(new Coordinate(groupId, artifactId, version, pkg, bundleName));
		}
		return result;
	}

	/**
	 * Reads the Export-Package manifest header and returns the plain package names
	 * (dropping anything after the first ';', trimming whitespace, skipping
	 * blanks).
	 */
	private static List<String> readExportedPackages(Path jarPath) {
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			var manifest = jar.getManifest();
			if (manifest == null)
				return List.of();

			String exportPackages = manifest.getMainAttributes().getValue(EXPORT_PACKAGE);
			if (exportPackages == null || exportPackages.isBlank())
				return List.of();

			List<String> result = new ArrayList<>();
			for (String entry : exportPackages.split(",")) {
				String clean = entry.trim();
				if (clean.isEmpty())
					continue;
				int semi = clean.indexOf(';');
				String pkg = (semi == -1) ? clean : clean.substring(0, semi).trim();
				if (!pkg.isEmpty()) {
					result.add(pkg);
				}
			}
			return result;
		} catch (IOException e) {
			return List.of();
		}
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
			String bsn = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);
			if (bsn == null || bsn.isBlank()) {
				return Optional.empty();
			}
			int semi = bsn.indexOf(';');
			return Optional.of((semi == -1) ? bsn : bsn.substring(0, semi));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	public static Optional<Coordinate> findByBundleName(String bundleName) {
		return coordList.stream().filter(c -> c.bundleName.equals(bundleName)).findFirst();
	}

	public static Optional<Coordinate> findByArtifactId(String artifactId) {
		return coordList.stream().filter(c -> c.artifactId.equals(artifactId)).findFirst();
	}

	public static Optional<Coordinate> findByPackage(List<Coordinate> coords, String packageName) {
		return coords.stream().filter(c -> c.packageName.equals(packageName)).findFirst();
	}
}