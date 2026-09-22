package tools.cipm.util.build.p2tm;

import java.io.File;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class MavenTest {
	@Test
	public void test() {
//		var map = NewConverter.buildArtifactToGroupIndexCache();
//		System.out.println(map.toString());
		var mvnDirPath = new File("../../mvn").toPath();
		var list = MvnRepositoryIndex.build(mvnDirPath);
		System.out.println(list);

		var differentBundleName = list.stream().filter(e -> !e.bundleName.equals(e.artifactId))
				.collect(Collectors.toList());
		System.out.println(System.lineSeparator() + System.lineSeparator() + System.lineSeparator() + differentBundleName);
		
		var pom = NewConverter.renderPom("abc", "def", "v0.0.0", MvnRepositoryIndex.getMvnRepositoryIndices());
		System.out.println(pom);
	}
}
