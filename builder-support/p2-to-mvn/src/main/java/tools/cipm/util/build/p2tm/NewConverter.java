package tools.cipm.util.build.p2tm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Scanner;

import org.openntf.maven.p2.model.P2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewConverter {
	private static final Logger logger = LoggerFactory.getLogger(NewConverter.class);
	private static final String JAR_FILE_EXTENSION = ".jar";
	private static final String URI_FILE_PREFIX = "file://";
	private static final String URI_HTTP_SCHEME = "http";
	private static final String URI_HTTPS_SCHEME = "https";

	public static void main(String[] args) {
//		installJarsFromLocalDirectory();
//		installJarsFromRemoteRepository();
//		installJarsFromRepository("", URI_FILE_PREFIX + "", true);
	}

	// ---------- unchanged: installJarsFromLocalDirectory() ----------
	public static void installJarsFromLocalDirectory() {
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
							fileNameParts[1].substring(0, fileNameParts[1].length() - JAR_FILE_EXTENSION.length()));
				} catch (IOException | InterruptedException e) {
					System.out.println("Could not process " + fileName);
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// ---------- unchanged: installJarsFromRemoteRepository() ----------
	public static void installJarsFromRemoteRepository() {
		String id = System.getProperty("p2tm.id");
		String repoUri = System.getProperty("p2tm.uri");
		if (id == null || repoUri == null) {
			System.out.println("No Id or URI given. Stopping.");
			return;
		}

		installJarsFromRepository(id, repoUri, false);
	}

	// ---------- unchanged: installJarsFromRepository() ----------
	public static void installJarsFromRepository(String id, String repoUri, boolean isLocal) {
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

	/**
	 * Installs a jar into the local Maven repository, generating and attaching a
	 * proper POM that lists the bundle's transitive dependencies.
	 */
	public static void installJarLocally(String filePath, String groupId, String artifactId, String version)
			throws IOException, InterruptedException {
		// Build the dependency list from the bundle's own manifest.
		var mvnDirPath = new File("../../mvn").toPath();
		var coords = MvnRepositoryIndex.build(mvnDirPath);

		// Serialize a POM that declares those dependencies.
		Path pomFile = buildPomFile(groupId, artifactId, version, coords);

		try {
			var subProcess = new ProcessBuilder("./mvnw", "install:install-file", "-DlocalRepositoryPath=../mvn",
					"-Dfile=" + filePath, "-DgroupId=" + groupId, "-DartifactId=" + artifactId, "-Dversion=" + version,
					"-Dpackaging=jar", "-DcreateChecksum=true", "-DpomFile=" + pomFile).inheritIO().start();
			var subProcessResult = subProcess.waitFor();
			System.out.println(subProcessResult);
		} finally {
			Files.deleteIfExists(pomFile);
		}
	}

	/**
	 * Serializes a minimal Maven POM that declares the given dependencies, writes
	 * it to a temp file in target/, and returns its path.
	 */
	public static Path buildPomFile(String groupId, String artifactId, String version, Collection<Coordinate> coords) {
		String xml = renderPom(groupId, artifactId, version, coords);

		Path targetDir = Paths.get("target");
		if (!Files.exists(targetDir)) {
			try {
				Files.createDirectories(targetDir);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		Path pomFile = targetDir.resolve(artifactId + "_" + version + "__pom.xml");
		try {
			Files.writeString(pomFile, xml);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return pomFile;
	}

	public static String renderPom(String groupId, String artifactId, String version, Collection<Coordinate> coords) {
		StringBuilder deps = new StringBuilder();
		if (coords != null) {
			for (Coordinate d : coords) {
				deps.append("    <dependency>\n").append("      <groupId>").append(xmlEscape(d.groupId))
						.append("</groupId>\n").append("      <artifactId>").append(xmlEscape(d.artifactId))
						.append("</artifactId>\n").append("      <version>").append(xmlEscape(d.version))
						.append("</version>\n").append("    </dependency>\n");
			}
		}

		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
				+ "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
				+ "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" + "  <modelVersion>4.0.0</modelVersion>\n"
				+ "  <groupId>" + xmlEscape(groupId) + "</groupId>\n" + "  <artifactId>" + xmlEscape(artifactId)
				+ "</artifactId>\n" + "  <version>" + xmlEscape(version) + "</version>\n"
				+ "  <packaging>jar</packaging>\n" + "  <dependencies>\n" + deps + "  </dependencies>\n"
				+ "</project>\n";
	}

	public static String xmlEscape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&apos;");
	}
}