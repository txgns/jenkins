package metanectar.provisioning.task;

import metanectar.model.MasterServer;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public class MasterStopThenTerminateTask extends MasterStopTask {

    public MasterStopThenTerminateTask(long timeout, MasterServer ms) {
        super(timeout, ms);
    }

    public MasterServerTask end(Future f) throws Exception {
        super.end(f);

        return new MasterTerminateTask(getTimeout(), ms, false);
    }
}
