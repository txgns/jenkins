package jenkins.install;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class UpgradeWizardTest {
    @Rule
    public final JenkinsRule j = new JenkinsRule();
    
    @After
    @Before
    public void rmStateFile() {
        File f = j.jenkins.getSetupWizard().getUpdateStateFile();
        if(f != null && f.exists()) {
            f.delete();
        }
    }

    @Test
    public void snooze() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                UpgradeWizard uw = newInstance();
                assertTrue(uw.isDue());
                uw.doSnooze();
                assertFalse(uw.isDue());

                return null;
            }
        });
    }

    /**
     * If not upgraded, the upgrade should cause some side effect.
     */
    @Test
    public void upgrade() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertTrue(j.jenkins.getUpdateCenter().getJobs().size() == 0);
                assertTrue(newInstance().isDue());

                // can't really test this because UC metadata is empty
                // assertTrue(j.jenkins.getUpdateCenter().getJobs().size() > 0);

                return null;
            }
        });
    }

    /**
     * If already upgraded, don't show anything
     */
    @Test
    public void fullyUpgraded() throws Exception {
        j.jenkins.getSetupWizard().setCurrentLevel(Jenkins.getVersion());
        assertFalse(newInstance()
                .isDue());
    }

    /**
     * If downgraded from future version, don't prompt upgrade wizard.
     */
    @Test
    public void downgradeFromFuture() throws Exception {
        FileUtils.writeStringToFile(j.jenkins.getSetupWizard().getUpdateStateFile(), "3.0");
        UpgradeWizard uw = newInstance();
        assertFalse(uw.isDue());
    }

    @Test
    public void freshInstallation() throws Exception {
        j.executeOnServer(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                UpgradeWizard uw = newInstance();
                assertTrue(uw.isDue());
                j.jenkins.getSetupWizard().setCurrentLevel(Jenkins.getVersion());
                assertFalse(uw.isDue());

                return null;
            }
        });
    }

    /**
     * Fresh instance of {@link UpgradeWizard} to test its behavior.
     */
    private UpgradeWizard newInstance() throws Exception {
        try {
            UpgradeWizard uw = new UpgradeWizard();
            uw.init();
            return uw;
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
