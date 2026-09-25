package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.List;

/**
 * A single OSGi bundle from the update site, together with its accurately
 * parsed OSGi manifest information.
 */
public final class UpdateSiteBundle {

	/** The OSGi symbolic name (bundle name). */
	public final String symbolicName;

	/** The bundle's version. */
	public final String version;

	/** The download URI for the bundle's JAR. */
	public final String uri;

	/** The bundle's Require-Bundle entries (each with a name and version range). */
	public final List<Dependency> requiredBundles;

	/** The bundle's Import-Package entries. */
	public final List<UpdateSitePackageRequirement> importedPackages;

	/** The bundle's Export-Package entries. */
	public final List<String> exportedPackages;

	public UpdateSiteBundle(String symbolicName, String version, String uri, List<Dependency> requiredBundles,
			List<UpdateSitePackageRequirement> importedPackages, List<String> exportedPackages) {
		this.symbolicName = symbolicName;
		this.version = version;
		this.uri = uri;
		this.requiredBundles = List.copyOf(requiredBundles);
		this.importedPackages = List.copyOf(importedPackages);
		this.exportedPackages = List.copyOf(exportedPackages);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof UpdateSiteBundle other))
			return false;
		return symbolicName.equals(other.symbolicName) && version.equals(other.version) && uri.equals(other.uri)
				&& requiredBundles.equals(other.requiredBundles) && importedPackages.equals(other.importedPackages)
				&& exportedPackages.equals(other.exportedPackages);
	}

	@Override
	public int hashCode() {
		int result = symbolicName.hashCode();
		result = 31 * result + version.hashCode();
		result = 31 * result + uri.hashCode();
		result = 31 * result + requiredBundles.hashCode();
		result = 31 * result + importedPackages.hashCode();
		result = 31 * result + exportedPackages.hashCode();
		return result;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Bundle{symbolicName='").append(symbolicName).append('\'').append(", version='").append(version)
				.append('\'').append(", uri='").append(uri).append('\'').append(", requiredBundles=")
				.append(requiredBundles).append(", importedPackages=").append(importedPackages)
				.append(", exportedPackages=").append(exportedPackages).append('}');
		return sb.toString();
	}
}