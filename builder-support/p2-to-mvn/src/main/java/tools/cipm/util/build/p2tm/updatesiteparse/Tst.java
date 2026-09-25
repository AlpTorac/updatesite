package tools.cipm.util.build.p2tm.updatesiteparse;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class Tst {
	@Test
	public void test() {
		var builder = new UpdateSiteBuilder();
		UpdateSite updateSite = null;
		try {
			updateSite = builder.buildLocal("/home/sdqstud1/CIPM-Updatesite/archive/cipm-0.1.1");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		System.out.println(updateSite);
	}
}
