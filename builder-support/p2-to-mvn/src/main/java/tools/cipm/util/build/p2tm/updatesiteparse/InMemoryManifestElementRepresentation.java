package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.Map;

import tools.cipm.util.build.p2tm.OsgiHeaders;

/**
 * An abstract class for classes that represent {@link ManifestElement}
 * instances.
 */
public abstract class InMemoryManifestElementRepresentation {
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
	 * Creates an instance with the given attributes and directives.
	 *
	 * @param attributes all attribute name/value pairs (may be {@code null})
	 * @param directives all directive name/value pairs (may be {@code null})
	 */
	public InMemoryManifestElementRepresentation(Map<String, String> attributes, Map<String, String> directives) {
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
		if (!(o instanceof InMemoryManifestElementRepresentation other))
			return false;
		return attributes.equals(other.attributes) && directives.equals(other.directives);
	}

	/**
	 * @return String version of all attributes stored in this instance
	 */
	protected String getAttributesString() {
		return "attributes=" + attributes;
	}

	/**
	 * @return String version of all directives stored in this instance
	 */
	protected String getDirectivesString() {
		return "directives=" + directives;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "{" + getAttributesString() + ", " + getDirectivesString() + '}';
	}
}
