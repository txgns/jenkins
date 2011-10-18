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
import metanectar.model.MasterTemplate;
import metanectar.model.MasterTemplateSource;
import metanectar.model.TemplateFile;
import metanectar.test.MetaNectarTestCase;
import org.acegisecurity.AccessDeniedException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;


/**
 * @author Paul Sandoz
 */
public class MasterTemplatePermissionsTest extends MetaNectarTestCase {
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
                    metaNectar.createMasterTemplate("m");
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
                metaNectar.createMasterTemplate("m");
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
                MasterTemplate ms = metaNectar.createMasterTemplate("m");
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
                MasterTemplate ms = metaNectar.createMasterTemplate("m");
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

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        wc.login("bob", "bob");

        List<MasterTemplate> items = wc.executeOnServer(new Callable<List<MasterTemplate>>() {
            public List<MasterTemplate> call() throws Exception {
                return hudson.getItems(MasterTemplate.class);
            }
        });

        assertTrue(items.isEmpty());
    }

    public void testPermissionToRead() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);
        createUser("bob", Hudson.READ, Item.READ);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        wc.login("bob", "bob");

        List<MasterTemplate> items = wc.executeOnServer(new Callable<List<MasterTemplate>>() {
            public List<MasterTemplate> call() throws Exception {
                return hudson.getItems(MasterTemplate.class);
            }
        });

        assertTrue(!items.isEmpty());
    }

    public void testNoPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(buttons.isEmpty());
    }

    public void testPermissionToConfigure() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterTemplate.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
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

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());
    }

    private static final class DummyMasterTemplateSource extends MasterTemplateSource {
        @Override
        public String getSourceDescription() {
            return null;
        }

        @Override
        public boolean canToTemplate() {
            return true;
        }

        @Override
        public TemplateFile toTemplate() throws IOException, InterruptedException {
            File f = File.createTempFile("prefix-", "-suffix");
            f.deleteOnExit();
            return new TemplateFile(f, "-suffix");
        }
    }

    public void testNoPermissionToCloneFromSource() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        ms.setConfiguredState(new DummyMasterTemplateSource());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                AccessDeniedException e = null;
                try {
                    ms.cloneFromSourceAction().get();
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testNoPermissionToClone() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterTemplate.CONFIGURE);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        ms.setConfiguredState(new DummyMasterTemplateSource());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                ms.cloneFromSourceAction().get();

                AccessDeniedException e = null;
                try {
                    ms.cloneToNewMasterAction("/", "mt");
                } catch (AccessDeniedException _e) {
                    e = _e;
                }
                assertNotNull(e);
                return null;
            }
        });
    }

    public void testPermissionToClone() throws Exception {
        configureSecurity();
        createUser("alice", Permission.READ, Item.CREATE, MasterTemplate.CONFIGURE, MasterTemplate.CLONE_MASTER);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        ms.setConfiguredState(new DummyMasterTemplateSource());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                ms.cloneFromSourceAction().get();
                ms.cloneToNewMasterAction("/", "mt");
                return null;
            }
        });

        List<MasterServer> items = wc.executeOnServer(new Callable<List<MasterServer>>() {
            public List<MasterServer> call() throws Exception {
                return hudson.getItems(MasterServer.class);
            }
        });

        assertEquals(1, items.size());
    }

    public void testPermissionToAdminister() throws Exception {
        configureSecurity();
        createUser("alice", Hudson.ADMINISTER);

        WebClient wc = new WebClient().login("alice", "alice");

        final MasterTemplate ms = wc.executeOnServer(new Callable<MasterTemplate>() {
            public MasterTemplate call() throws Exception {
                return metaNectar.createMasterTemplate("m");
            }
        });

        HtmlPage configPage = wc.goTo(ms.getUrl() + "configure");
        HtmlForm form = configPage.getFormByName("config");
        List<HtmlElement> buttons = form.getHtmlElementsByTagName("button");
        assertTrue(!buttons.isEmpty());

        ms.setConfiguredState(new DummyMasterTemplateSource());

        wc.executeOnServer(new Callable<Void>() {
            public Void call() throws Exception {
                ms.cloneFromSourceAction().get();
                ms.cloneToNewMasterAction("/", "mt");
                return null;
            }
        });

        List<MasterServer> items = wc.executeOnServer(new Callable<List<MasterServer>>() {
            public List<MasterServer> call() throws Exception {
                return hudson.getItems(MasterServer.class);
            }
        });

        assertEquals(1, items.size());
    }

}
