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
public final class Coordinate implements Comparable<Coordinate> {
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

	public String dependencyKey() {
		return groupId + ":" + artifactId;
	}

	@Override
	public int compareTo(Coordinate other) {
		int byGroup = this.groupId.compareTo(other.groupId);
		if (byGroup != 0) {
			return byGroup;
		}
		int byArtifact = this.artifactId.compareTo(other.artifactId);
		if (byArtifact != 0) {
			return byArtifact;
		}
		int byVersion = this.version.compareTo(other.version);
		if (byVersion != 0) {
			return byVersion;
		}
		return this.bundleName.compareTo(other.bundleName);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Coordinate other)) {
			return false;
		}
		return groupId.equals(other.groupId) && artifactId.equals(other.artifactId) && version.equals(other.version)
				&& bundleName.equals(other.bundleName);
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version
				+ (bundleName.equals(artifactId) ? "" : "  (bundle " + bundleName + ")");
	}
}
