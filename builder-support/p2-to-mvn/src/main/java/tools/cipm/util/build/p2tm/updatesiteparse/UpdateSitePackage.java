package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.Map;
import java.util.Objects;

import tools.cipm.util.build.p2tm.OsgiHeaders;

/**
 * A single package export or import entry, parsed from the Export-Package or
 * Import-Package manifest header.
 *
 * <p>
 * Both headers share the same structure: a package name plus optional
 * attributes (such as {@code version="..."}) and directives (such as
 * {@code resolution:=optional}), so a single type models both directions.
 * </p>
 *
 * <p>
 * For completeness, all attributes and directives declared on the package entry
 * are preserved in their respective maps, so no information is lost even for
 * qualifiers this class does not interpret directly (e.g. {@code uses:=},
 * {@code x-internal:=}, {@code mandatory}, etc.).
 * </p>
 */
public final class UpdateSitePackage {

	/** The package name. */
	public final String packageName;

	/**
	 * All attribute name/value pairs declared on this package entry (e.g.
	 * {@code version}, {@code bundle-symbolic-name}, {@code mandatory}).
	 * Unmodifiable. The {@code version} is also mirrored here.
	 */
	public final Map<String, String> attributes;

	/**
	 * All directive name/value pairs declared on this package entry (e.g.
	 * {@code resolution}, {@code uses}, {@code x-internal}). Unmodifiable.
	 */
	public final Map<String, String> directives;

	/**
	 * Creates a package requirement with the given attributes and directives.
	 *
	 * @param packageName the package name
	 * @param attributes  all attribute name/value pairs (may be {@code null})
	 * @param directives  all directive name/value pairs (may be {@code null})
	 */
	public UpdateSitePackage(String packageName, Map<String, String> attributes,
			Map<String, String> directives) {
		this.packageName = Objects.requireNonNull(packageName, "packageName");
		this.attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
		this.directives = Map.copyOf(directives == null ? Map.of() : directives);
	}

	/**
	 * The parsed {@code version} attribute, or {@link VersionRange#any()} if
	 * absent.
	 */
	public VersionRange getVersionRange() {
		return VersionRange.parse(attributes.get(OsgiHeaders.VERSION));
	}

	/**
	 * @return Whether this package dependency is declared as optional
	 */
	public boolean isOptional() {
		return OsgiHeaders.OPTIONAL.equals(directives.get(OsgiHeaders.RESOLUTION));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof UpdateSitePackage other))
			return false;
		return packageName.equals(other.packageName) && attributes.equals(other.attributes)
				&& directives.equals(other.directives);
	}

	@Override
	public String toString() {
		return "UpdateSitePackageRequirement{" + "packageName='" + packageName + '\'' + ", attributes=" + attributes
				+ ", directives=" + directives + '}';
	}
}