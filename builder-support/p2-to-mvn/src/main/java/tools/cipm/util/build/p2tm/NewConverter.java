package tools.cipm.util.build.p2tm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.openntf.maven.p2.model.P2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.cipm.util.build.p2tm.mvnosgimap.Coordinate;
import tools.cipm.util.build.p2tm.mvnosgimap.MvnRepositoryIndex;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSite;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBuilder;
import tools.cipm.util.build.p2tm.updatesiteparse.UpdateSiteBundle;

public class NewConverter {
	private static final Logger logger = LoggerFactory.getLogger(NewConverter.class);
	private static final String JAR_FILE_EXTENSION = ".jar";
	private static final String URI_FILE_PREFIX = "file://";
	private static final String URI_HTTP_SCHEME = "http";
	private static final String URI_HTTPS_SCHEME = "https";

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
						+ ". Please enter it. Leave the ID empty if the last group ID "
						+ lastGroupIdContainer.toString() + " should be reused.");
				var potentialGroupId = scanner.next();
				if (!potentialGroupId.isBlank()) {
					lastGroupIdContainer.delete(0, lastGroupIdContainer.length());
					lastGroupIdContainer.append(potentialGroupId);
				}

				try {
					installJarLocally(path.toString(), lastGroupIdContainer.toString(), fileNameParts[0],
							fileNameParts[1].substring(0, fileNameParts[1].length() - JAR_FILE_EXTENSION.length()));
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

		HttpClient client = HttpClient.newHttpClient();
		for (var bundle : allBundles) {
			System.out.println("Downloading " + bundle.getId());

			try {
				if (isLocal) {
					var bundleUri = bundle.getUri("");
					if (!URI_HTTP_SCHEME.equals(bundleUri.getScheme())
							&& !URI_HTTPS_SCHEME.equals(bundleUri.getScheme()) && Files.exists(Paths.get(bundleUri))) {
						installJarLocally(bundleUri.getSchemeSpecificPart(), id, bundle.getId(), bundle.getVersion());
					}
					continue;
				}

				var tempStorageFile = Paths.get("target", bundle.getId() + "_" + bundle.getVersion() + ".jar");

				if (Files.notExists(tempStorageFile)) {
					var bodyHandler = BodyHandlers.ofFile(tempStorageFile);
					client.send(HttpRequest.newBuilder(bundle.getUri("")).GET().build(), bodyHandler);
				}

				installJarLocally(tempStorageFile.toString(), id, bundle.getId(), bundle.getVersion());

				Files.delete(tempStorageFile);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void installJarLocally(String filePath, String groupId, String artifactId, String version)
			throws IOException, InterruptedException {
		// Only supports Linux for now.
		var subProcess = new ProcessBuilder("./mvnw", "install:install-file", "-DlocalRepositoryPath=../mvn",
				"-Dfile=" + filePath, "-DgroupId=" + groupId, "-DartifactId=" + artifactId, "-Dversion=" + version,
				"-Dpackaging=jar", "-DcreateChecksum=true").inheritIO().start();
		var subProcessResult = subProcess.waitFor();
		System.out.println(subProcessResult);
	}

	private static String getTransitiveDependencyPOMString() {
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

		return PomWriter.render("abc", "def", "v0.0.0", allTransitiveDeps);
	}
}