package tools.cipm.util.build.p2tm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.cipm.util.build.p2tm.mvnosgimap.Coordinate;
import tools.cipm.util.build.p2tm.mvnosgimap.MvnRepositoryIndex;
import tools.cipm.util.build.p2tm.updatesiteparse.Dependency;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBundle;

/**
 * Derives the Maven dependencies of an {@link UpdateSiteBundle} from its
 * {@code Require-Bundle} requirements, resolving each required bundle to a
 * concrete {@link Coordinate} in the {@link MvnRepositoryIndex}.
 */
public final class TransitiveDependencyResolver {
	/**
	 * Resolves and returns the deduped transitive Maven dependencies of the given
	 * bundle.
	 *
	 * @param bundle the bundle whose requirements to resolve
	 * @return a deduped list of coordinates (never null)
	 */
	public List<Coordinate> resolve(UpdateSiteBundle bundle) {
		Set<String> seen = new LinkedHashSet<>(); // dedup by bundle name
		List<Coordinate> result = new ArrayList<>();

		for (Dependency required : bundle.requiredBundles) {
			String bundleName = required.bundleName;

			// Skip duplicate required bundles (e.g. org.eclipse.ocl.ecore
			// appearing twice in a manifest).
			if (!seen.add(bundleName)) {
				continue;
			}

			// Prefer a concrete coordinate from the Maven repository.
			var coords = MvnRepositoryIndex.findByBundleName(bundleName);
			Coordinate coord = null;
			if (!coords.isEmpty()) {
				// Not installed yet: fall back to default group, no version.
//				String group = bundleToGroup.getOrDefault(bundleName, defaultGroup);
//				coord = new Coordinate(group, bundleName, "0", bundleName);
//				MvnRepositoryIndex.getMvnRepositoryIndices().add(coord);
//			} else {
				coord = coords.stream().findFirst().get();
				result.add(coord);
			}
		}
		return result;
	}
}