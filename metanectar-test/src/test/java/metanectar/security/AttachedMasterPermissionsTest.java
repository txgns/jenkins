package metanectar.security;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import metanectar.model.AttachedMaster;
import metanectar.test.MetaNectarTestCase;
import org.acegisecurity.AccessDeniedException;

import java.util.List;
import java.util.concurrent.Callable;


/**
 * @author Paul Sandoz
 */
public class AttachedMasterPermissionsTest extends MetaNectarTestCase {
    HudsonPrivateSecurityRealm realm;

    GlobalMatrixAuthorizationStrategy auth;

    private void configureSecurity() {
        realm = new HudsonPrivateSecurityRealm(false);
        hudson.setSecurityRealm(realm);

        auth = new GlobalMatrixAuthorizationStrategy();
        hudson.setAuthorizationStrategy(auth);
    }

    private void createUser(String user, Permission... ps) throws Exception {
        realm.createAccount(user, user);

        for (Permission p : ps) {
            auth.add(p, user);
        }
    }

    public void testNoPermissionToCreate() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AccessDeniedException e = null;
                try {
                    metaNectar.createAttachedMaster("m");
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testPermissionToCreate() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                metaNectar.createAttachedMaster("m");
                return null;
            }
        });
    }

    public void testNoPermissionToDelete() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AttachedMaster ms = metaNectar.createAttachedMaster("m");
                AccessDeniedException e = null;
                try {
                    ms.delete();
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testPermissionToDelete() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, Item.DELETE);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AttachedMaster ms = metaNectar.createAttachedMaster("m");
                ms.delete();
                return null;
            }
        });
    }

    public void testNoPermissionToRead() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        wc.login("bob", "bob");

        List<AttachedMaster> items = wc.executeOnServer(new Callable<List<AttachedMaster>>() {
            public List<AttachedMaster> call() throws Exception {
                return hudson.getItems(AttachedMaster.class);
            }
        });

        assertTrue(items.isEmpty());
    }

    public void testPermissionToRead() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ, Item.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        wc.login("bob", "bob");

        List<AttachedMaster> items = wc.executeOnServer(new Callable<List<AttachedMaster>>() {
            public List<AttachedMaster> call() throws Exception {
                return hudson.getItems(AttachedMaster.class);
            }
        });

        assertTrue(!items.isEmpty());
    }

    public void testNoPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(buttons.isEmpty());
    }

    public void testPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, AttachedMaster.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

    public void testPermissionToConfigureItem() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, Item.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

    public void testPermissionToAdminister() throws Exception {
        configureSecurity();
        createUser("alice", Hudson.ADMINISTER);

        WebClient wc = new WebClient().login("alice", "alice");

        final AttachedMaster ms = wc.executeOnServer(new Callable<AttachedMaster>() {
            public AttachedMaster call() throws Exception {
                return metaNectar.createAttachedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

}
