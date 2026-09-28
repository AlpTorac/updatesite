package tools.cipm.util.build.p2tm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import tools.cipm.util.build.p2tm.mvnosgimap.Coordinate;

/**
 * Renders and writes a Maven POM that declares a bundle's transitive
 * dependencies.
 */
public final class PomWriter {

	private PomWriter() {
	}

	/**
	 * Writes a POM to a temp file under target/ and returns its path.
	 */
	public static Path write(String groupId, String artifactId, String version, Collection<Coordinate> dependencies)
			throws IOException {
		String xml = render(groupId, artifactId, version, dependencies);
		Path targetDir = Paths.get("target");
		if (!Files.exists(targetDir)) {
			Files.createDirectories(targetDir);
		}
		Path pomFile = targetDir.resolve(artifactId + "_" + version + "__pom.xml");
		Files.writeString(pomFile, xml);
		return pomFile;
	}

	public static String render(String groupId, String artifactId, String version,
			Collection<Coordinate> dependencies) {
		StringBuilder deps = new StringBuilder();
		if (dependencies != null) {
			for (Coordinate c : dependencies) {
				// Skip self-dependency.
				if (c.artifactId.equals(artifactId) && c.groupId.equals(groupId)) {
					continue;
				}
				deps.append("    <dependency>\n").append("      <groupId>").append(xmlEscape(c.groupId))
						.append("</groupId>\n").append("      <artifactId>").append(xmlEscape(c.artifactId))
						.append("</artifactId>\n").append("      <version>").append(xmlEscape(c.version))
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
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&apos;");
	}
}