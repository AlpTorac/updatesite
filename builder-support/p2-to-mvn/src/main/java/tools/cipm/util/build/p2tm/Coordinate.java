package tools.cipm.util.build.p2tm;

/**
 * The full identity of an installed bundle, derived from the ../mvn repository.
 *
 * <p>
 * A single {@code Coordinate} carries <em>all</em> the identifiers a generated
 * POM dependency needs, plus how the bundle is named:
 * </p>
 * <ul>
 * <li><b>groupId</b> – from the repository directory path</li>
 * <li><b>artifactId</b> – from the artifact's parent directory name</li>
 * <li><b>version</b> – from the artifact's directory / filename</li>
 * <li><b>bundleName</b> – from the jar's OSGi Bundle-SymbolicName (falls back
 * to the artifactId when not an OSGi bundle)</li>
 * </ul>
 */
public final class Coordinate {
	public final String groupId;
	public final String artifactId;
	public final String version;
	public final String bundleName;

	public Coordinate(String groupId, String artifactId, String version, String bundleName) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.bundleName = bundleName;
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version
				+ (bundleName.equals(artifactId) ? "" : "  (bundle " + bundleName + ")");
	}
}
