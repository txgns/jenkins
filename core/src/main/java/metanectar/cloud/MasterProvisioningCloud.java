package metanectar.cloud;

import hudson.Extension;
import hudson.model.*;
import hudson.slaves.*;
import metanectar.model.MetaNectar;
import metanectar.slave.MasterProvisioningSlave;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A master provisioning dumb slave cloud.
 * <p>
 * TODO hook up UI
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningCloud extends Cloud {
    private final int delay;

    private final String remoteFS;

    private final String command;

    @DataBoundConstructor
    public MasterProvisioningCloud(int delay, String remoteFS, String command) {
        super("master-provisioning-cloud");
        this.delay = delay;
        this.remoteFS = remoteFS;
        this.command = command;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

        for( ; excessWorkload>0; excessWorkload-- ) {
            final String slaveName = name+"-slave#"+excessWorkload;
            r.add(new NodeProvisioner.PlannedNode(slaveName,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            Thread.sleep(delay);

                            System.out.println("launching slave");

                            MasterProvisioningSlave s = new MasterProvisioningSlave(
                                    slaveName,
                                    "",
                                    remoteFS,
                                    "1",
                                    Node.Mode.NORMAL,
                                    "_masters_",
                                    new CommandLauncher(command),
                                    RetentionStrategy.INSTANCE,
                                    new ArrayList<NodeProperty<?>>()
                            );

                            Hudson.getInstance().addNode(s);
                            s.toComputer().connect(false).get();
                            return s;
                       }
                    })
                    ,1));
        }
        return r;
    }

    @Override
    public boolean canProvision(Label label) {
        return label.equals(MetaNectar.getInstance().masterProvisioner.masterLabel);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public String getDisplayName() {
            return "Master Provisioning Dumb Cloud";
        }
    }

}