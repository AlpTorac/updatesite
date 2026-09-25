package tools.cipm.util.build.p2tm.updatesiteparse;

/**
 * A package-level requirement, from an Import-Package header.
 */
public final class UpdateSitePackageRequirement {
	public final String packageName;
	public final String versionRange; // may be ""
	public final boolean optional; // resolution:=optional

	public UpdateSitePackageRequirement(String packageName, String versionRange, boolean optional) {
		this.packageName = packageName;
		this.versionRange = versionRange == null ? "" : versionRange;
		this.optional = optional;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateSitePackageRequirement other)) return false;
        return optional == other.optional
            && packageName.equals(other.packageName)
            && versionRange.equals(other.versionRange);
    }

    @Override
    public int hashCode() {
        int result = packageName.hashCode();
        result = 31 * result + versionRange.hashCode();
        result = 31 * result + (optional ? 1231 : 1237);
        return result;
    }
	
	@Override
	public String toString() {
		return "PackageReqt{" + "packageName='" + packageName + '\'' + ", versionRange='" + versionRange + '\''
				+ ", optional=" + optional + '}';
	}
}