package metanectar.provisioning.task;

import metanectar.model.MasterServer;

/**
 * @author Paul Sandoz
 */
public class MasterStopThenTerminateTask extends MasterStopTask {

    public MasterStopThenTerminateTask(MasterServer ms) {
        super(ms);
    }

    public MasterServerTask end() throws Exception {
        super.end();

        return new MasterTerminateTask(ms, false);
    }
}
