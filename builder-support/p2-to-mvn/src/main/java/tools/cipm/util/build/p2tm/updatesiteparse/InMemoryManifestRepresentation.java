package tools.cipm.util.build.p2tm.updatesiteparse;

import java.util.Map;

public abstract class InMemoryManifestRepresentation {
	/**
	 * The entire manifest file contents. Stored here, since it is already parsed in
	 * the process.
	 */
	private Map<String, String> entireManifest;

	public void setEntireManifest(Map<String, String> entireManifest) {
		this.entireManifest = Map.copyOf(entireManifest == null ? Map.of() : entireManifest);
	}

	public Map<String, String> getEntireManifest() {
		return Map.copyOf(entireManifest);
	}
}
