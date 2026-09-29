package tools.cipm.util.build.p2tm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openntf.maven.p2.model.P2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.cipm.util.build.p2tm.mvnosgimap.Coordinate;
import tools.cipm.util.build.p2tm.mvnosgimap.MvnRepositoryIndex;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSite;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBuilder;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBundle;

public class MavenTest {
	@Disabled
	@Test
	public void test() {
//		var map = NewConverter.buildArtifactToGroupIndexCache();
//		System.out.println(map.toString());

		var mvnDirPath = new File("../../mvn").toPath();
		var list = MvnRepositoryIndex.build(mvnDirPath);
		System.out.println(list);

		var differentBundleName = list.stream().filter(e -> !e.bundleName.equals(e.artifactId))
				.collect(Collectors.toList());
		System.out.println(
				System.lineSeparator() + System.lineSeparator() + System.lineSeparator() + differentBundleName);

		var onlyBundle = list.stream().filter(e -> !e.hasPackage()).collect(Collectors.toList());
		System.out.println(System.lineSeparator() + System.lineSeparator() + System.lineSeparator() + onlyBundle);

		var pom = PomWriter.render("abc", "def", "v0.0.0", MvnRepositoryIndex.findAllBundles());
		System.out.println(pom);
	}

	private static final Logger logger = LoggerFactory.getLogger(MavenTest.class);

	@Test
	public void testWithMapping() {
		var mvnDirPath = new File("../../mvn").toPath();
		var repoPath = new File("/home/sdqstud1/CIPM-Updatesite/archive/cipm-0.1.1").getAbsoluteFile().toPath()
				.toAbsolutePath();

		// Build the Maven Repository Index
		MvnRepositoryIndex.build(mvnDirPath);

		// Parse the update site into an in-memory model.
		UpdateSite updateSite;
		try {
			updateSite = new UpdateSiteBuilder().buildLocal(repoPath.toString());
		} catch (IOException e) {
			throw new RuntimeException("Could not parse update site", e);
		}

		P2Repository p2Repo = P2Repository.getInstance(URI.create(updateSite.repositoryUri), logger);
		var allBundles = p2Repo.getBundles();

		TransitiveDependencyResolver resolver = new TransitiveDependencyResolver();

		Set<Coordinate> allTransitiveDeps = new HashSet<>();

		for (var p2 : allBundles) {
			UpdateSiteBundle bundle = updateSite.bundlesByName.get(p2.getId());
			List<Coordinate> transitiveDepsForBundle = bundle != null ? resolver.resolve(bundle) : List.of();
			allTransitiveDeps.addAll(transitiveDepsForBundle);
		}

		var pom = PomWriter.render("abc", "def", "v0.0.0", allTransitiveDeps);
		System.out.println(pom);
	}
}
