package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.List;

import tools.cipm.util.build.p2tm.BundleSymbolicNameData;

public final class UpdateSiteBundle extends InMemoryManifestRepresentation {
	public final BundleSymbolicNameData symbolicName;
	public final String uri;
	public final List<BundleDependency> requiredBundles;
	public final List<UpdateSitePackage> importedPackages;
	public final List<UpdateSitePackage> exportedPackages;

	public UpdateSiteBundle(BundleSymbolicNameData symbolicName, String uri, List<BundleDependency> requiredBundles,
			List<UpdateSitePackage> importedPackages, List<UpdateSitePackage> exportedPackages) {
		this.symbolicName = symbolicName;
		this.uri = uri;
		this.requiredBundles = List.copyOf(requiredBundles);
		this.importedPackages = List.copyOf(importedPackages);
		this.exportedPackages = List.copyOf(exportedPackages);
	}

	public boolean isSingleton() {
		return this.symbolicName.isSingleton();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof UpdateSiteBundle other))
			return false;
		return symbolicName.equals(other.symbolicName) && uri.equals(other.uri)
				&& requiredBundles.equals(other.requiredBundles) && importedPackages.equals(other.importedPackages)
				&& exportedPackages.equals(other.exportedPackages);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append("{symbolicName='").append(symbolicName).append('\'')
				.append(", uri='").append(uri).append('\'').append(", requiredBundles=").append(requiredBundles)
				.append(", importedPackages=").append(importedPackages).append(", exportedPackages=")
				.append(exportedPackages).append('}');
		return sb.toString();
	}
}