package hudson.cloudbees;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import org.jvnet.hudson.test.HudsonTestCase;

import javax.servlet.http.HttpServletResponse;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class UnsecureSecondaryDomainRootActionTest extends HudsonTestCase {

    private URL file;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        b.getWorkspace().child("x").write("Hello","UTF-8");

        file = new URL(getURL(), "job/" + p.getName() + "/ws/x");
    }

    public void testBasics() throws Exception {
        // create some file in the workspace
        FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        b.getWorkspace().child("x").write("Hello","UTF-8");

        URL file = new URL(getURL(), "job/" + p.getName() + "/ws/x");
        Page page = createWebClient().getPage(file);
        verifyContent(page);

        // set up the privilege separation and make sure it works
        setUpPrivilegeSeparatoin();
        page = createWebClient().getPage(file);
        verifyContent(page);
        verifyRedirected(page);
    }

    private void setUpPrivilegeSeparatoin() {
        UnsecureSecondaryDomain.get().setUrl("http://127.0.0.3:"+localPort+contextPath+"/");
    }

    public void testSecuredNectar() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        hudson.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        hudson.setAuthorizationStrategy(auth);
        auth.add(Hudson.ADMINISTER,"alice");

        setUpPrivilegeSeparatoin();

        // this should fail since the access is restricted
        try {
            Page p = createWebClient().getPage(file);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getStatusCode());
        }

        // but this should work, and 127.0.0.3 shouldn't require additional authentication
        realm.createAccount("alice","alice");
        WebClient wc = createWebClient().login("alice", "alice");
        Page page = wc.getPage(file);
        verifyContent(page);
        verifyRedirected(page);

    }

    private void verifyContent(Page page) {
        assertEquals("Hello",page.getWebResponse().getContentAsString());
    }

    private void verifyRedirected(Page page) {
        assertTrue(page.getWebResponse().getUrl().toExternalForm().startsWith("http://127.0.0.3:"));
    }

}
