package tools.cipm.util.build.p2tm.updatesiteparse;

/**
 * A bundle-level dependency, from a Require-Bundle header.
 */
import java.util.Objects;

public final class Dependency {
    public final String bundleName;
    public final VersionRange versionRange;   // parsed bundle-version

    public Dependency(String bundleName, String version) {
        this.bundleName = bundleName;
        this.versionRange = VersionRange.parse(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency other)) return false;
        return bundleName.equals(other.bundleName)
            && Objects.equals(versionRange, other.versionRange);
    }

    @Override
    public int hashCode() {
        int result = bundleName.hashCode();
        result = 31 * result + Objects.hashCode(versionRange);
        return result;
    }

    @Override
    public String toString() {
        return "Dependency{"
            + "bundleName='" + bundleName + '\''
            + ", versionRange=" + versionRange
            + '}';
    }
}