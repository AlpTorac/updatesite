package tools.cipm.util.build.p2tm.updatesiteparse;

/**
 * A bundle-level dependency, from a Require-Bundle header.
 */
public final class Dependency {
	public final String bundleName; // required bundle's symbolic name
	public final String versionRange; // e.g. "[1.0.0,2.0.0)" or ""

	public Dependency(String bundleName, String versionRange) {
		this.bundleName = bundleName;
		this.versionRange = versionRange == null ? "" : versionRange;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency other)) return false;
        return bundleName.equals(other.bundleName)
            && versionRange.equals(other.versionRange);
    }

    @Override
    public int hashCode() {
        int result = bundleName.hashCode();
        result = 31 * result + versionRange.hashCode();
        return result;
    }
	
	@Override
	public String toString() {
		return "Dependency{" + "bundleName='" + bundleName + '\'' + ", versionRange='" + versionRange + '\'' + '}';
	}
}