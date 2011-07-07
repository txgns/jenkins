package metanectar.provisioning;

import metanectar.model.MasterTemplate;
import metanectar.model.MasterTemplateSource;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class ManagedTemplateTest extends AbstractMasterProvisioningTestCase {

    public void testTemplate() throws Exception {
        MasterTemplate mt = metaNectar.createMasterTemplate("mt");
        mt.setConfiguredState(new TestMasterTemplateSource());

        mt.cloneFromSourceAction().get(1, TimeUnit.MINUTES);
        assertEquals(MasterTemplate.State.Cloned, mt.getState());
    }

    static class TestMasterTemplateSource extends MasterTemplateSource {
        @Override
        public String getSourceDescription() {
            return "";
        }

        @Override
        public boolean canToTemplate() {
            return true;
        }

        @Override
        public File toTemplate() throws IOException, InterruptedException {
            Thread.currentThread().sleep(100);
            return new File("/tmp/template.zip");
        }
    }

    public void testRecoverFromCloning() throws Exception {
        MasterTemplate mt = metaNectar.createMasterTemplate("mt");
        WaitingMasterTemplateSource source = new WaitingMasterTemplateSource();
        mt.setConfiguredState(source);

        mt.cloneFromSourceAction();
        source.waitingLatch.await(1, TimeUnit.MINUTES);

        assertEquals(MasterTemplate.State.Cloning, mt.getState());

        metaNectar.masterProvisioner.getMasterServerTaskQueue().getQueue().clear();
        source.waiting = false;

        mt.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterTemplate.State.Cloned, mt.getState());
    }

    static class WaitingMasterTemplateSource extends MasterTemplateSource {
        CountDownLatch waitingLatch = new CountDownLatch(1);

        boolean waiting = true;

        @Override
        public String getSourceDescription() {
            return "";
        }

        @Override
        public boolean canToTemplate() {
            return true;
        }

        @Override
        public File toTemplate() throws IOException, InterruptedException {
            if (waiting) {
                waitingLatch.countDown();
                while (true) {
                    try {
                        Thread.currentThread().sleep(TimeUnit.HOURS.toMillis(1));
                    } catch (InterruptedException e) {}
                }
            } else {
                Thread.currentThread().sleep(100);
                return new File("/tmp/template.zip");
            }
        }
    }
}
