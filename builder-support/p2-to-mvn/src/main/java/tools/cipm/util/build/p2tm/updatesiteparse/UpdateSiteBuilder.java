package tools.cipm.util.build.p2tm.updatesiteparse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.eclipse.osgi.util.ManifestElement;
import org.openntf.maven.p2.model.P2Bundle;
import org.openntf.maven.p2.model.P2Repository;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a complete {@link UpdateSite} by combining p2-layout-resolver
 * (UpdateSiteBundle enumeration) with org.eclipse.osgi.util.ManifestElement
 * (accurate manifest parsing).
 */
public final class UpdateSiteBuilder {
	private static final String JAR_EXTENSION = ".jar";

//	private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

	private static final Logger logger = LoggerFactory.getLogger(UpdateSiteBuilder.class);

	private final HttpClient http;

	public UpdateSiteBuilder() {
		this.http = HttpClient.newHttpClient();
	}

	/**
	 * Builds the in-memory representation of the given p2 update site.
	 *
	 * @param repositoryUri the p2 repository URI
	 * @return the fully parsed {@link UpdateSite}
	 */
	public UpdateSite build(String repositoryUri) throws IOException, InterruptedException {
		P2Repository repo = P2Repository.getInstance(URI.create(repositoryUri), logger);

		Map<String, UpdateSiteBundle> bundlesByName = new LinkedHashMap<>();
		Map<UpdateSitePackageRequirement, List<UpdateSiteBundle>> packagesToBundles = new LinkedHashMap<>();

		for (P2Bundle p2 : repo.getBundles()) {
			UpdateSiteBundle UpdateSiteBundle = parseRemoteBundle(p2);
			bundlesByName.put(UpdateSiteBundle.symbolicName, UpdateSiteBundle);

			for (UpdateSitePackageRequirement pkg : UpdateSiteBundle.exportedPackages) {
				packagesToBundles.computeIfAbsent(pkg, k -> new ArrayList<>()).add(UpdateSiteBundle);
			}
		}

		return new UpdateSite(repositoryUri, bundlesByName, packagesToBundles);
	}

	/**
	 * Builds the in-memory representation of a LOCAL update-site clone.
	 *
	 * @param localCloneRoot the filesystem path to the cloned update site
	 *                       (containing artifacts.xml/jar and plugins/)
	 */
	public UpdateSite buildLocal(String localCloneRoot) throws IOException {
		// P2Repository handles file:// transparently via P2Util (non-HTTP branch).
		String repositoryUri = "file://" + Paths.get(localCloneRoot).toAbsolutePath() + "/";
		P2Repository repo = P2Repository.getInstance(URI.create(repositoryUri), logger);

		Map<String, UpdateSiteBundle> bundlesByName = new LinkedHashMap<>();
		Map<UpdateSitePackageRequirement, List<UpdateSiteBundle>> packagesToBundles = new LinkedHashMap<>();

		for (P2Bundle p2 : repo.getBundles()) {
			UpdateSiteBundle bundle = parseLocalBundle(p2, localCloneRoot);
			bundlesByName.put(bundle.symbolicName, bundle);
			for (UpdateSitePackageRequirement pkg : bundle.exportedPackages) {
				packagesToBundles.computeIfAbsent(pkg, k -> new ArrayList<>()).add(bundle);
			}
		}

		return new UpdateSite(repositoryUri, bundlesByName, packagesToBundles);
	}

	private UpdateSiteBundle parseRemoteBundle(P2Bundle p2) throws IOException, InterruptedException {
		String uri = p2.getUri("").toString();
		Path tmp = downloadToTemp(uri);

		try (JarFile jar = new JarFile(tmp.toFile())) {
			InputStream is = jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF"));
			// ManifestElement.parseBundleManifest fills a Map<String,String> of
			// raw header values (no localization; just raw OSGi headers).
			Map<String, String> headers = parseBundleManifest(is);

			List<Dependency> required = parseRequireBundle(headers.get(OsgiHeaders.REQUIRE_BUNDLE));
			List<UpdateSitePackageRequirement> imported = parseImportPackage(headers.get(OsgiHeaders.IMPORT_PACKAGE));
			List<UpdateSitePackageRequirement> exported = parseExportPackage(headers.get(OsgiHeaders.EXPORT_PACKAGE));

			return new UpdateSiteBundle(p2.getId(), p2.getVersion(), uri, required, imported, exported);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/**
	 * Reads a bundle's JAR directly from the local plugins/ directory and parses
	 * its manifest via ManifestElement.
	 */
	private UpdateSiteBundle parseLocalBundle(P2Bundle p2, String localCloneRoot) throws IOException {
		// P2Bundle.getUri("") for a file:// base yields
		// file:///<root>/plugins/<id>_<version>.jar
		// Derive the local file path from that URI.
		URI jarUri = p2.getUri("");
		Path jarPath = Paths.get(jarUri); // works for file:// URIs

		try (JarFile jar = new JarFile(jarPath.toFile())) {
			InputStream is = jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF"));

			Map<String, String> headers = parseBundleManifest(is);

			List<Dependency> required = parseRequireBundle(headers.get(OsgiHeaders.REQUIRE_BUNDLE));
			List<UpdateSitePackageRequirement> imported = parseImportPackage(headers.get(OsgiHeaders.IMPORT_PACKAGE));
			List<UpdateSitePackageRequirement> exported = parseExportPackage(headers.get(OsgiHeaders.EXPORT_PACKAGE));

			return new UpdateSiteBundle(p2.getId(), p2.getVersion(), jarUri.toString(), required, imported, exported);
		}
	}

	private static Map<String, String> parseBundleManifest(InputStream is) {
		Map<String, String> headers = new LinkedHashMap<>();
		try {
			ManifestElement.parseBundleManifest(is, headers);
		} catch (IOException | BundleException e) {
			throw new IllegalStateException(e);
		}
		return headers;
	}

	private Path downloadToTemp(String uri) throws IOException, InterruptedException {
		Path tmp = Files.createTempFile("p2bundle", JAR_EXTENSION);
		HttpResponse<Path> resp = http.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(),
				HttpResponse.BodyHandlers.ofFile(tmp));
		if (resp.statusCode() != 200) {
			throw new IOException("Failed to download " + uri + ": HTTP " + resp.statusCode());
		}
		return tmp;
	}

	// ------------------------------------------------------------------
	// OSGi manifest parsing via ManifestElement
	// ------------------------------------------------------------------

	private static ManifestElement[] parseHeader(String header, String value) {
		ManifestElement[] headers = null;
		try {
			headers = ManifestElement.parseHeader(OsgiHeaders.REQUIRE_BUNDLE, value);
		} catch (BundleException e) {
			throw new IllegalStateException(e);
		}
		return headers;
	}

	private static List<Dependency> parseRequireBundle(String value) {
		List<Dependency> result = new ArrayList<>();
		if (value == null)
			return result;
		for (ManifestElement el : parseHeader(OsgiHeaders.REQUIRE_BUNDLE, value)) {
			String name = el.getValue();
			String version = el.getAttribute(OsgiHeaders.BUNDLE_VERSION);
			result.add(new Dependency(name, version == null ? "" : version));
		}
		return result;
	}

	private static List<UpdateSitePackageRequirement> parseImportPackage(String value) {
		List<UpdateSitePackageRequirement> result = new ArrayList<>();
		if (value == null)
			return result;
		for (ManifestElement el : parseHeader(OsgiHeaders.IMPORT_PACKAGE, value)) {
			String version = el.getAttribute(OsgiHeaders.VERSION);
			boolean optional = OsgiHeaders.OPTIONAL.equals(el.getDirective(OsgiHeaders.RESOLUTION));
			result.add(new UpdateSitePackageRequirement(el.getValue(), version, optional));
		}
		return result;
	}

	private static List<UpdateSitePackageRequirement> parseExportPackage(String value) {
		List<UpdateSitePackageRequirement> result = new ArrayList<>();
		if (value == null)
			return result;
		for (ManifestElement el : parseHeader(OsgiHeaders.EXPORT_PACKAGE, value)) {
			String version = el.getAttribute(OsgiHeaders.VERSION);
			// Export-Package uses uses:= and x-friends:= directives; for parity
			// with imports we capture the version attribute. Exports are not
			// "optional" in the same sense, so optional stays false.
			result.add(new UpdateSitePackageRequirement(el.getValue(), version, false));
		}
		return result;
	}
}