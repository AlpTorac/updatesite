package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, parsed representation of a version range.
 *
 * <p>
 * Supports both OSGi and Maven range syntax, e.g.
 * </p>
 * <ul>
 * <li>{@code 1.2.3} — a single (exact) version, no bounds</li>
 * <li>{@code [1.0,2.0)} — inclusive lower, exclusive upper</li>
 * <li>{@code [1.0,2.0]} — inclusive both</li>
 * <li>{@code (1.0,2.0)} — exclusive both</li>
 * <li>{@code [1.0,)} / {@code [1.0,∞)} — inclusive lower, unbounded upper</li>
 * <li>{@code (,2.0]} — unbounded lower, inclusive upper</li>
 * </ul>
 */
public final class VersionRange {

	private final String lowerVersion; // may be null when unbounded
	private final boolean includeLower;
	private final String upperVersion; // may be null when unbounded
	private final boolean includeUpper;
	private final boolean bounded; // false when it's a single exact version

	private VersionRange(String lowerVersion, boolean includeLower, String upperVersion, boolean includeUpper,
			boolean bounded) {
		this.lowerVersion = lowerVersion;
		this.includeLower = includeLower;
		this.upperVersion = upperVersion;
		this.includeUpper = includeUpper;
		this.bounded = bounded;
	}

	/**
	 * The unconstrained "any version" range: {@code [0,∞)}.
	 *
	 * @return a range matching any version
	 */
	public static VersionRange any() {
		return new VersionRange("0", true, null, false, true);
	}

	/**
	 * A single exact version (no bounds), e.g. {@code "1.2.3"}.
	 *
	 * @param version the exact version string
	 * @return a non-null unbounded single-version range
	 */
	public static VersionRange exact(String version) {
		String v = version == null ? "" : version.trim();
		return new VersionRange(v, true, null, false, false);
	}

	/**
	 * Parses a version-range string into a {@link VersionRange}.
	 *
	 * @param value the raw header value
	 * @return the parsed range; always non-null
	 * @throws IllegalArgumentException if {@code value} is non-blank but malformed
	 *                                  (e.g. an unterminated or comma-less
	 *                                  bracketed range)
	 */
	public static VersionRange parse(String value) {
		// Null / empty / blank -> "any" version.
		if (value == null || value.isBlank()) {
			return any();
		}

		String s = value.trim();

		// A single version without brackets — an exact version.
		if (s.charAt(0) != '[' && s.charAt(0) != '(') {
			return exact(s);
		}

		// Bracketed range, e.g. "[1.0,2.0)" / "[1.0,2.0]" / "[1.0,)".
		int comma = s.indexOf(',');
		if (comma == -1) {
			throw new IllegalArgumentException("Malformed version range (missing ','): " + value);
		}
		if (!s.endsWith("]") && !s.endsWith(")")) {
			throw new IllegalArgumentException("Malformed version range (missing closing bracket): " + value);
		}

		char open = s.charAt(0);
		char close = s.charAt(s.length() - 1);
		boolean includeLower = (open == '[');
		boolean includeUpper = (close == ']');

		String lower = normalizeBound(s.substring(1, comma).trim());
		String upper = normalizeBound(s.substring(comma + 1, s.length() - 1).trim());

		return new VersionRange(lower, includeLower, upper, includeUpper, true);
	}

	/** Returns null for empty/∞ bounds (unbounded). */
	private static String normalizeBound(String raw) {
		if (raw == null)
			return null;
		String t = raw.trim();
		if (t.isEmpty() || t.equals("\u221E") || t.equals("INF")) {
			return null;
		}
		return t;
	}

	/**
	 * @return the lower bound version, or {@code null} if unbounded
	 */
	public String getLowerVersion() {
		return lowerVersion;
	}

	/**
	 * @return {@code true} if the lower bound is inclusive ({@code [})
	 */
	public boolean isIncludeLower() {
		return includeLower;
	}

	/**
	 * @return the upper bound version, or {@code null} if unbounded
	 */
	public String getUpperVersion() {
		return upperVersion;
	}

	/**
	 * @return {@code true} if the upper bound is inclusive ({@code ]})
	 */
	public boolean isIncludeUpper() {
		return includeUpper;
	}

	/**
	 * @return {@code true} if this is a bounded range (i.e. not a single exact
	 *         version)
	 */
	public boolean isBounded() {
		return bounded;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof VersionRange other))
			return false;
		return includeLower == other.includeLower && includeUpper == other.includeUpper && bounded == other.bounded
				&& Objects.equals(lowerVersion, other.lowerVersion) && Objects.equals(upperVersion, other.upperVersion);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(lowerVersion);
		result = 31 * result + (includeLower ? 1 : 0);
		result = 31 * result + Objects.hashCode(upperVersion);
		result = 31 * result + (includeUpper ? 1 : 0);
		result = 31 * result + (bounded ? 1 : 0);
		return result;
	}

	@Override
	public String toString() {
		if (!bounded) {
			return lowerVersion;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(includeLower ? '[' : '(');
		sb.append(lowerVersion == null ? "" : lowerVersion);
		sb.append(',');
		sb.append(upperVersion == null ? "" : upperVersion);
		sb.append(includeUpper ? ']' : ')');
		return sb.toString();
	}
}