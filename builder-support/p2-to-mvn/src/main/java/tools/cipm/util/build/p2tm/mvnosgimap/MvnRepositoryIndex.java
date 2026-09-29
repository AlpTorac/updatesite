package tools.cipm.util.build.p2tm.mvnosgimap;

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
import java.util.stream.Collectors;

/**
 * Builds a flat, complete index of the local Maven repository as a single list
 * of {@link Coordinate}s.
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
		// The "org.pcm.headless.api" bundle is the only exception in the local Maven
		// repository that only declares the manifest version, hence its mapping is
		// manually added.
		ARTIFACT_TO_BUNDLE_NAME.put("api", "org.pcm.headless.api");
	}

	private static Set<Coordinate> coordList;

	private MvnRepositoryIndex() {
	}

	public static Collection<Coordinate> getMvnRepositoryIndices() {
		return coordList;
	}

	/**
	 * Builds one {@link Coordinate} per installed bundle in the repository (in the
	 * form of a JAR file).
	 *
	 * <p>
	 * Each Maven artifact is stored at:
	 * 
	 * <pre>
	 *   &lt;relativeLocalMavenRepoRootPath&gt;/&lt;groupPath&gt;/&lt;artifactId&gt;/&lt;version&gt;/&lt;artifactId&gt;-&lt;version&gt;.jar
	 * </pre>
	 *
	 * A single walk of the local Maven repository derives every field from that
	 * layout plus the jar's OSGi {@code Bundle-SymbolicName}.
	 *
	 * @param relativeLocalMavenRepoRootPath The repository root (currently
	 *                                       {@code Paths.get("../../mvn")})
	 *                                       relative to the execution path
	 * @return the list of coordinates for every installed bundle
	 * @throws IOException if the local Maven repository cannot be walked
	 */
	public static Collection<Coordinate> build(Path relativeLocalMavenRepoRootPath) {
		var coordinates = new HashSet<Coordinate>();

		try (var stream = Files.walk(relativeLocalMavenRepoRootPath)) {
			stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(JAR_EXTENSION))
					.forEach(jarPath -> coordinates.addAll(coordinatesFor(jarPath, relativeLocalMavenRepoRootPath)));
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
	 * 
	 * @param jarPath                        The path to the JAR file
	 * @param relativeLocalMavenRepoRootPath The repository root (currently
	 *                                       {@code Paths.get("../../mvn")})
	 *                                       relative to the execution path
	 * @return A list of all Maven coordinates for the given JAR file (at jarPath).
	 *         The entire JAR file (i.e. the entire bundle) will have one coordinate
	 *         and each of its exported packages will have their own coordinates.
	 */
	private static List<Coordinate> coordinatesFor(Path jarPath, Path relativeLocalMavenRepoRootPath) {
		// Derive the shared Maven identity
		Path artifactDir = jarPath.getParent();
		if (artifactDir == null)
			return List.of();

		String versionDir = artifactDir.getFileName().toString();
		Path artifactIdDir = artifactDir.getParent();
		Path groupPath = artifactIdDir == null ? null : artifactIdDir.getParent();
		var localMavenRepoRootDepth = relativeLocalMavenRepoRootPath.getNameCount();
		if (artifactIdDir == null || groupPath == null || groupPath.getNameCount() < localMavenRepoRootDepth) {
			return List.of();
		}

		String artifactId = artifactIdDir.getFileName().toString();
		String groupId = relativeLocalMavenRepoRootPath.relativize(groupPath).toString().replace('/', '.');

		String fileName = jarPath.getFileName().toString();
		String version = versionDir;
		if (fileName.startsWith(artifactId + "-") && fileName.endsWith(JAR_EXTENSION)) {
			version = fileName.substring(artifactId.length() + 1, fileName.length() - JAR_EXTENSION.length());
		}

		// --- bundle name (manifest, or curated override, or artifactId fallback) ---
		String bundleName = readBundleSymbolicName(jarPath)
				.orElseGet(() -> ARTIFACT_TO_BUNDLE_NAME.getOrDefault(artifactId, artifactId));

		List<Coordinate> result = new ArrayList<>();

		// One Coordinate for the bundle itself
		result.add(new Coordinate(groupId, artifactId, version, bundleName));

		// One Coordinate per exported package
		for (String pkg : readExportedPackages(jarPath)) {
			result.add(new Coordinate(groupId, artifactId, version, pkg, bundleName));
		}
		return result;
	}

	/**
	 * Reads the Export-Package manifest header and returns the plain package names
	 * (dropping anything after the first ';', trimming whitespace, skipping
	 * blanks).
	 * 
	 * @param jarPath The path to the JAR file
	 * 
	 * @return The list of all exported packages under the manifest file of the
	 *         given JAR file (at jarPath).
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
	 * 
	 * @param jarPath The path to the JAR file
	 * 
	 * @return The symbolic bundle name in the manifest file of the given JAR file
	 *         (at jarPath).
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

	/**
	 * @return A list of all Maven coordinates within the local Maven repository.
	 */
	public static List<Coordinate> findAllBundles() {
		return coordList.stream().filter(c -> !c.hasPackage()).collect(Collectors.toList());
	}

	/**
	 * @return A list of all Maven coordinates within the local Maven repository
	 *         with the matching bundle name.
	 */
	public static List<Coordinate> findByBundleName(String bundleName) {
		return coordList.stream().filter(c -> c.bundleName.equals(bundleName)).collect(Collectors.toList());
	}
}