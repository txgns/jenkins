package metanectar.provisioning.task;

import metanectar.model.MasterServer;

/**
 * @author Paul Sandoz
 */
public class MasterWaitForQuietDownThenStopTask extends MasterWaitForQuietDownTask {

    public MasterWaitForQuietDownThenStopTask(long timeout, MasterServer ms) {
        super(timeout, ms);
    }

    public MasterServerTask end() throws Exception {
        super.end();

        // Continue with stop if the wait for quiet down was not cancelled
        return isCancelled() ? null : new MasterStopTask(getTimeout(), ms);
    }
}
