package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.List;
import java.util.Objects;

public final class UpdateSiteBundle {
    public final String symbolicName;
    public final String version;
    public final String uri;
    public final List<Dependency> requiredBundles;
    public final List<UpdateSitePackageRequirement> importedPackages;
    public final List<UpdateSitePackageRequirement> exportedPackages;

    public UpdateSiteBundle(String symbolicName, String version, String uri,
                            List<Dependency> requiredBundles,
                            List<UpdateSitePackageRequirement> importedPackages,
                            List<UpdateSitePackageRequirement> exportedPackages) {
        this.symbolicName = symbolicName;
        this.version = version;
        this.uri = uri;
        this.requiredBundles = List.copyOf(requiredBundles);
        this.importedPackages = List.copyOf(importedPackages);
        this.exportedPackages = List.copyOf(exportedPackages);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateSiteBundle other)) return false;
        return symbolicName.equals(other.symbolicName)
            && version.equals(other.version)
            && uri.equals(other.uri)
            && requiredBundles.equals(other.requiredBundles)
            && importedPackages.equals(other.importedPackages)
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
        sb.append("UpdateSiteBundle{symbolicName='").append(symbolicName).append('\'')
          .append(", version='").append(version).append('\'')
          .append(", uri='").append(uri).append('\'')
          .append(", requiredBundles=").append(requiredBundles)
          .append(", importedPackages=").append(importedPackages)
          .append(", exportedPackages=").append(exportedPackages)
          .append('}');
        return sb.toString();
    }
}