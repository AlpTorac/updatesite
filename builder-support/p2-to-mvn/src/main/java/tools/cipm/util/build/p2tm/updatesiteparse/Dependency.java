package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.Objects;

/**
 * A bundle-level dependency, from a Require-Bundle header.
 */
public final class Dependency {
	/**
	 * The name of the bundle
	 */
	public final String bundleName;
	/**
	 * The parsed bundle-version range
	 */
	public final VersionRange versionRange;

	/**
	 * Constructs an instance that encapsulates a dependency to the given bundle
	 * 
	 * @param bundleName The name of the bundle
	 * @param version    The version range of the bundle as String
	 */
	public Dependency(String bundleName, String version) {
		this.bundleName = bundleName;
		this.versionRange = VersionRange.parse(version);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Dependency other))
			return false;
		return bundleName.equals(other.bundleName) && Objects.equals(versionRange, other.versionRange);
	}

	@Override
	public String toString() {
		return "Dependency{" + "bundleName='" + bundleName + '\'' + ", versionRange=" + versionRange + '}';
	}
}