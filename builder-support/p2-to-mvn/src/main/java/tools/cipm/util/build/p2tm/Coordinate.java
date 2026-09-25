package tools.cipm.util.build.p2tm;

import java.util.Objects;

/**
 * The identity of a single exported package within an installed bundle.
 *
 * <p>
 * Each {@code Coordinate} represents <em>one</em> exported package, so a bundle
 * that exports several packages is represented by several {@code Coordinate}
 * instances that share the same {@code groupId}, {@code artifactId},
 * {@code version} and {@code bundleName} but differ in {@code packageName}.
 * </p>
 *
 * <p>
 * The class is {@link Comparable} and provides {@link #equals(Object)} /
 * {@link #hashCode()} based on the full package-level identity. It can be used
 * both to look up the bundle providing an {@code Import-Package} dependency
 * (via {@link #packageName}) and to resolve whole-bundle dependencies (via
 * {@link #bundleName}).
 * </p>
 */
public final class Coordinate implements Comparable<Coordinate> {
	public static final String DEFAULT_EXPORTED_PACKAGE_NAME = "";

	/** The Maven group ID of the bundle that exports {@link #packageName}. */
	public final String groupId;

	/** The Maven artifact ID of the bundle that exports {@link #packageName}. */
	public final String artifactId;

	/** The Maven version of the bundle that exports {@link #packageName}. */
	public final String version;

	/**
	 * The single exported package this coordinate represents. Never {@code null}.
	 */
	public final String packageName;

	/**
	 * The OSGi symbolic name of the bundle that exports {@link #packageName}. Never
	 * {@code null}.
	 */
	public final String bundleName;

	public Coordinate(String groupId, String artifactId, String version, String bundleName) {
		this(groupId, artifactId, version, DEFAULT_EXPORTED_PACKAGE_NAME, bundleName);
	}

	/**
	 * Creates a new {@code Coordinate}.
	 *
	 * @param groupId     the Maven group ID of the exporting bundle
	 * @param artifactId  the Maven artifact ID of the exporting bundle
	 * @param version     the Maven version of the exporting bundle
	 * @param packageName the exported package; may be empty string when the bundle
	 *                    exports no packages
	 * @param bundleName  the OSGi symbolic name of the exporting bundle
	 */
	public Coordinate(String groupId, String artifactId, String version, String packageName, String bundleName) {
		this.groupId = Objects.requireNonNull(groupId, "groupId");
		this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
		this.version = Objects.requireNonNull(version, "version");
		// Null-safe: fall back to empty string when no package is exported.
		this.packageName = (packageName == null || packageName.isBlank()) ? DEFAULT_EXPORTED_PACKAGE_NAME : packageName;
		this.bundleName = Objects.requireNonNull(bundleName, "bundleName");
	}

	/**
	 * Returns a stable key identifying this coordinate's Maven artifact,
	 * independent of version and package.
	 *
	 * @return {@code groupId + ":" + artifactId}
	 */
	public String dependencyKey() {
		return groupId + ":" + artifactId;
	}

	public boolean hasPackage() {
		return !this.packageName.isBlank() && !this.packageName.equals(DEFAULT_EXPORTED_PACKAGE_NAME);
	}

	/**
	 * Compares coordinates by package name, then group, artifact, version and
	 * finally bundle name. Since each coordinate represents one exported package,
	 * {@link #packageName} is the primary ordering key.
	 *
	 * @param o the coordinate to compare against; must not be {@code null}
	 * @return a negative, zero, or positive value per {@link Comparable}
	 */
	@Override
	public int compareTo(Coordinate o) {
		int byPackage = this.packageName.compareTo(o.packageName);
		if (byPackage != 0)
			return byPackage;
		int byGroup = this.groupId.compareTo(o.groupId);
		if (byGroup != 0)
			return byGroup;
		int byArtifact = this.artifactId.compareTo(o.artifactId);
		if (byArtifact != 0)
			return byArtifact;
		int byVersion = this.version.compareTo(o.version);
		if (byVersion != 0)
			return byVersion;
		return this.bundleName.compareTo(o.bundleName);
	}

	/**
	 * Returns {@code true} if the given object is another {@code Coordinate} with
	 * the same {@link #groupId}, {@link #artifactId}, {@link #version},
	 * {@link #packageName} and {@link #bundleName}.
	 *
	 * @param o the object to compare with
	 * @return {@code true} if structurally equal, {@code false} otherwise
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Coordinate other))
			return false;
		return groupId.equals(other.groupId) && artifactId.equals(other.artifactId) && version.equals(other.version)
				&& packageName.equals(other.packageName) && bundleName.equals(other.bundleName);
	}

	/**
	 * Computes a hash code consistent with {@link #equals(Object)} over all five
	 * identity fields.
	 *
	 * <p>
	 * FIXME AI insisted to add this, not sure whether it is actually necessary /
	 * correct
	 *
	 * @return the hash code of this coordinate
	 */
	@Override
	public int hashCode() {
		int result = groupId.hashCode();
		result = 31 * result + artifactId.hashCode();
		result = 31 * result + version.hashCode();
		result = 31 * result + packageName.hashCode();
		result = 31 * result + bundleName.hashCode();
		return result;
	}

	/**
	 * Returns a human-readable representation of this coordinate.
	 *
	 * @return a string of the form
	 *         {@code "package  <-  groupId:artifactId:version (bundle ...)"}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (hasPackage()) {
			sb.append(packageName + "  <-  ");
		}
		sb.append(groupId + ":" + artifactId + ":" + version);
		if (!bundleName.equals(artifactId)) {
			sb.append("  (bundle " + bundleName + ")");
		}
		return sb.toString();
	}
}