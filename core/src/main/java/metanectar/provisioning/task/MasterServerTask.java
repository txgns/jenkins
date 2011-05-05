package metanectar.provisioning.task;

import metanectar.model.MasterServer;

/**
 * @author Paul Sandoz
 */
public abstract class MasterServerTask<F> extends FutureTask<F, MasterServerTask> {

    protected final MasterServer ms;

    protected final MasterServer.Action action;

    public MasterServerTask(MasterServer ms, MasterServer.Action action) {
        this.ms = ms;
        this.action = action;
    }

    public MasterServer getMasterServer() {
        return ms;
    }

    public MasterServer.Action getAction() {
        return action;
    }
}
