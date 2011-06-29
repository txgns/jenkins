package metanectar.provisioning.task;

import metanectar.model.MasterServer;

/**
 * @author Paul Sandoz
 */
public class MasterStopThenTerminateTask extends MasterStopTask {

    public MasterStopThenTerminateTask(long timeout, MasterServer ms) {
        super(timeout, ms);
    }

    public MasterServerTask end() throws Exception {
        super.end();

        return new MasterTerminateTask(getTimeout(), ms, false);
    }
}
