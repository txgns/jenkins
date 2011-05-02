package metanectar.provisioning.task;

import metanectar.model.MasterServer;

/**
 * @author Paul Sandoz
 */
public abstract class MasterServerTask<F> extends FutureTask<F, MasterServerTask> {

    protected final MasterServer ms;

    public MasterServerTask(MasterServer ms) {
        this.ms = ms;
    }

    public MasterServer getMasterServer() {
        return ms;
    }
}
