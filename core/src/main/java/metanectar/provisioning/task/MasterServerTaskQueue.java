package metanectar.provisioning.task;

import com.google.common.collect.Lists;
import hudson.model.Node;
import metanectar.model.MasterServer;

import java.util.List;

/**
 * @author Paul Sandoz
 */
public class MasterServerTaskQueue extends TaskQueue<MasterServerTask> {
    public List<MasterServer> getProvisioning(Node n) {
        List<MasterServer> l = Lists.newArrayList();

        for (MasterServerTask ms : getQueue()) {
            if (ms.getMasterServer().getState() == MasterServer.State.Provisioning &&
                    ms.getMasterServer().getNode() == n) {
                l.add(ms.getMasterServer());
            }
        }

        return l;
    }
}
