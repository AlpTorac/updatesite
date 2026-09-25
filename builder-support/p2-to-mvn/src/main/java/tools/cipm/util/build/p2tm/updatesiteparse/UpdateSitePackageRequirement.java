package tools.cipm.util.build.p2tm.updatesiteparse;

/**
 * A single package export or import entry, parsed from the Export-Package or
 * Import-Package manifest header.
 *
 * <p>
 * Both headers share the same structure: a package name plus optional
 * attributes (such as {@code version="..."}) and directives (such as
 * {@code resolution:=optional}), so a single type models both directions.
 * </p>
 */
import java.util.Objects;

public final class UpdateSitePackageRequirement {
    public final String packageName;
    public final VersionRange versionRange;   // parsed; null when absent
    public final boolean optional;

    public UpdateSitePackageRequirement(String packageName, String version, boolean optional) {
        this.packageName = packageName;
        this.versionRange = VersionRange.parse(version);
        this.optional = optional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateSitePackageRequirement other)) return false;
        return optional == other.optional
            && packageName.equals(other.packageName)
            && Objects.equals(versionRange, other.versionRange);
    }

    @Override
    public int hashCode() {
        int result = packageName.hashCode();
        result = 31 * result + Objects.hashCode(versionRange);
        result = 31 * result + (optional ? 1231 : 1237);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateSitePackageRequirement{"
            + "packageName='" + packageName + '\''
            + ", versionRange=" + versionRange
            + ", optional=" + optional
            + '}';
    }
}