package metanectar.security;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.TopLevelItemDescriptor;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import metanectar.model.SharedSlave;
import metanectar.test.MetaNectarTestCase;
import org.acegisecurity.AccessDeniedException;

import java.util.List;
import java.util.concurrent.Callable;


/**
 * @author Paul Sandoz
 */
public class SharedSlavePermissionsTest extends MetaNectarTestCase {
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

    private SharedSlave createSharedSlave(String name) throws Exception {
        return (SharedSlave)hudson.createProject(TopLevelItemDescriptor.all().get(SharedSlave.DescriptorImpl.class), name);
    }

    public void testNoPermissionToCreate() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AccessDeniedException e = null;
                try {
                    createSharedSlave("m");
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
                createSharedSlave("m");
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
                SharedSlave ms = createSharedSlave("m");
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
                SharedSlave ms = createSharedSlave("m");
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

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        wc.login("bob", "bob");

        List<SharedSlave> items = wc.executeOnServer(new Callable<List<SharedSlave>>() {
            public List<SharedSlave> call() throws Exception {
                return hudson.getItems(SharedSlave.class);
            }
        });

        assertTrue(items.isEmpty());
    }

    public void testPermissionToRead() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ, Item.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        wc.login("bob", "bob");

        List<SharedSlave> items = wc.executeOnServer(new Callable<List<SharedSlave>>() {
            public List<SharedSlave> call() throws Exception {
                return hudson.getItems(SharedSlave.class);
            }
        });

        assertTrue(!items.isEmpty());
    }

    public void testNoPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");

        ElementNotFoundException e = null;
        try {
            form.getButtonByCaption("Save");
        } catch (ElementNotFoundException _e) {
            e = _e;
        }
        assertNotNull(e);
    }

    public void testPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, SharedSlave.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        assertNotNull(form.getButtonByCaption("Save"));
    }

    public void testPermissionToConfigureItem() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, Item.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        assertNotNull(form.getButtonByCaption("Save"));
    }

    public void testPermissionToAdminister() throws Exception {
        configureSecurity();
        createUser("alice", Hudson.ADMINISTER);

        WebClient wc = new WebClient().login("alice", "alice");

        final SharedSlave ms = wc.executeOnServer(new Callable<SharedSlave>() {
            public SharedSlave call() throws Exception {
                return createSharedSlave("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        assertNotNull(form.getButtonByCaption("Save"));
    }

}