package tools.cipm.util.build.p2tm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openntf.maven.p2.model.P2Bundle;
import org.openntf.maven.p2.model.P2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.cipm.util.build.p2tm.mvnosgimap.Coordinate;
import tools.cipm.util.build.p2tm.mvnosgimap.MvnRepositoryIndex;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSite;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBuilder;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBundle;

/**
 * Converts a p2 update site into a Maven repository (../mvn). For each bundle
 * this generates a POM that declares its transitive dependencies (derived from
 * the bundle's OSGi requirements and resolved against the existing Maven
 * repository), so downstream Maven builds resolve them automatically.
 */
public class NewConverter {
	private static final Logger logger = LoggerFactory.getLogger(NewConverter.class);
	private static final String JAR_FILE_EXTENSION = ".jar";

	// Optional fallback group mapping for bundles not yet in ../mvn.
	private static final Map<String, String> BUNDLE_TO_GROUP = Map.of();

	public static void main(String[] args) {
		// installJarsFromLocalDirectory();
		// installJarsFromRemoteRepository();
		// installJarsFromRepository("", URI_FILE_PREFIX + "", true);
	}

	private static void installJarsFromLocalDirectory() {
		var consideredPath = Paths.get("target", "jars");
		if (Files.notExists(consideredPath)) {
			System.out.println("Cannot consider the directory. It does not exist.");
			return;
		}

		try (var scanner = new Scanner(System.in)) {
			var lastGroupIdContainer = new StringBuilder();
			Files.walk(consideredPath).filter(Files::isRegularFile).forEach(path -> {
				var fileName = path.getFileName().toString();
				if (!fileName.endsWith(JAR_FILE_EXTENSION)) {
					return;
				}
				var fileNameParts = fileName.split("_");
				System.out.println("You need to specify a group ID for the artifact " + fileName
						+ ". Please enter it. Leave the ID empty if the last group ID " + lastGroupIdContainer
						+ " should be reused.");
				var potentialGroupId = scanner.next();
				if (!potentialGroupId.isBlank()) {
					lastGroupIdContainer.setLength(0);
					lastGroupIdContainer.append(potentialGroupId);
				}
				try {
					installJarLocally(path.toString(), lastGroupIdContainer.toString(), fileNameParts[0],
							fileNameParts[1].substring(0, fileNameParts[1].length() - JAR_FILE_EXTENSION.length()),
							List.of());
				} catch (IOException | InterruptedException e) {
					System.out.println("Could not process " + fileName);
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void installJarsFromRemoteRepository() {
		String id = System.getProperty("p2tm.id");
		String repoUri = System.getProperty("p2tm.uri");
		if (id == null || repoUri == null) {
			System.out.println("No Id or URI given. Stopping.");
			return;
		}
		installJarsFromRepository(id, repoUri, false);
	}

	private static void installJarsFromRepository(String id, String repoUri, boolean isLocal) {
		P2Repository p2Repo = P2Repository.getInstance(URI.create(repoUri), logger);
		var allBundles = p2Repo.getBundles();

		// Parse the update site into an in-memory model.
		UpdateSite updateSite;
		try {
			updateSite = new UpdateSiteBuilder().buildLocal(localRootFromUri(repoUri));
		} catch (IOException e) {
			throw new RuntimeException("Could not parse update site", e);
		}

		// Index the existing Maven repository.
		MvnRepositoryIndex.build(Paths.get("..", "mvn"));

		TransitiveDependencyResolver resolver = new TransitiveDependencyResolver();

		HttpClient client = HttpClient.newHttpClient();
		for (P2Bundle p2 : allBundles) {
			System.out.println("Downloading " + p2.getId());
			try {
				Path jarPath;
				if (isLocal) {
					jarPath = Paths.get(p2.getUri(""));
					if (!Files.exists(jarPath)) {
						continue;
					}
				} else {
					jarPath = Paths.get("target", p2.getId() + "_" + p2.getVersion() + ".jar");
					if (Files.notExists(jarPath)) {
						client.send(HttpRequest.newBuilder(p2.getUri("")).GET().build(), BodyHandlers.ofFile(jarPath));
					}
				}

				UpdateSiteBundle bundle = updateSite.bundlesByName.get(p2.getId());
				List<Coordinate> deps = bundle != null ? resolver.resolve(bundle) : List.of();

				installJarLocally(jarPath.toString(), id, p2.getId(), p2.getVersion(), deps);

				if (!isLocal) {
					Files.delete(jarPath);
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static String localRootFromUri(String repoUri) {
		if (repoUri.startsWith("file:")) {
			return repoUri.substring("file:".length());
		}
		return repoUri;
	}

	private static void installJarLocally(String filePath, String groupId, String artifactId, String version,
			List<Coordinate> dependencies) throws IOException, InterruptedException {
		Path pomFile = PomWriter.write(groupId, artifactId, version, dependencies);

		try {
			var subProcess = new ProcessBuilder("./mvnw", "install:install-file", "-DlocalRepositoryPath=../mvn",
					"-Dfile=" + filePath, "-DgroupId=" + groupId, "-DartifactId=" + artifactId, "-Dversion=" + version,
					"-Dpackaging=jar", "-DcreateChecksum=true", "-DpomFile=" + pomFile).inheritIO().start();
			var result = subProcess.waitFor();
			System.out.println(result);
		} finally {
			Files.deleteIfExists(pomFile);
		}
	}
}