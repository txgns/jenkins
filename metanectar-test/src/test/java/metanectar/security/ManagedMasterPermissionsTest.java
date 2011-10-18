package metanectar.security;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import metanectar.model.MasterServer;
import metanectar.provisioning.AbstractMasterProvisioningTestCase;
import org.acegisecurity.AccessDeniedException;

import java.util.List;
import java.util.concurrent.Callable;


/**
 * @author Paul Sandoz
 */
public class ManagedMasterPermissionsTest extends AbstractMasterProvisioningTestCase {
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
                    metaNectar.createManagedMaster("m");
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
                metaNectar.createManagedMaster("m");
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
                MasterServer ms = metaNectar.createManagedMaster("m");
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
                MasterServer ms = metaNectar.createManagedMaster("m");
                ms.delete();
                return null;
            }
        });
    }

    public void testNoPermissionToManage() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                MasterServer ms = metaNectar.createManagedMaster("m");

                AccessDeniedException e = null;
                try {
                    ms.provisionAction();
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testPermissionToManage() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterServer.MANAGE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                MasterServer ms = metaNectar.createManagedMaster("m");
                return ms.provisionAction().get().startAction().get();
            }
        });

        assertEquals(MasterServer.State.Started, ms.getState());

        wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return ms.stopAction().get().terminateAction(false).get();
            }
        });

        assertEquals(MasterServer.State.Terminated, ms.getState());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
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

    public void testNoPermissionToLifeCycle() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterServer.MANAGE);
        createUser("bob", Permission.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                MasterServer ms = metaNectar.createManagedMaster("m");
                return ms.provisionAction().get();
            }
        });

        wc.login("bob", "bob");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AccessDeniedException e = null;
                try {
                    ms.startAction();
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testPermissionToLifeCycle() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterServer.MANAGE);
        createUser("bob", Permission.READ, MasterServer.LIFE_CYCLE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                MasterServer ms = metaNectar.createManagedMaster("m");
                return ms.provisionAction().get();
            }
        });

        wc.login("bob", "bob");

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                ms.startAction().get().stopAction().get();
                return null;
            }
        });

        assertEquals(MasterServer.State.Stopped, ms.getState());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AccessDeniedException e = null;
                try {
                    ms.terminateAction(true).get();
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testNoPermissionToRead() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return metaNectar.createManagedMaster("m");
            }
        });

        wc.login("bob", "bob");

        List<MasterServer> items = wc.executeOnServer(new Callable<List<MasterServer>>() {
            public List<MasterServer> call() throws Exception {
                return hudson.getItems(MasterServer.class);
            }
        });

        assertTrue(items.isEmpty());
    }

    public void testPermissionToRead() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ, Item.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return metaNectar.createManagedMaster("m");
            }
        });

        wc.login("bob", "bob");

        List<MasterServer> items = wc.executeOnServer(new Callable<List<MasterServer>>() {
            public List<MasterServer> call() throws Exception {
                return hudson.getItems(MasterServer.class);
            }
        });

        assertTrue(!items.isEmpty());
    }

    public void testNoPermissionToConfigure() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return metaNectar.createManagedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(buttons.isEmpty());
    }

    public void testPermissionToConfigure() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterServer.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return metaNectar.createManagedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

    public void testPermissionToConfigureItem() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, Item.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                return metaNectar.createManagedMaster("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

    public void testPermissionToAdminister() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar(1);

        configureSecurity();
        createUser("alice", Hudson.ADMINISTER);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterServer ms = wc.executeOnServer(new Callable<MasterServer>() {
            public MasterServer call() throws Exception {
                MasterServer ms = metaNectar.createManagedMaster("m");
                return ms.provisionAction().get().startAction().get().stopAction().get().terminateAction(false).get();
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

}
