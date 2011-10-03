package metanectar.provisioning.task;

import metanectar.model.MasterServer;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public class MasterWaitForQuietDownThenStopTask extends MasterWaitForQuietDownTask {

    public MasterWaitForQuietDownThenStopTask(long timeout, MasterServer ms) {
        super(timeout, ms);
    }

    public MasterServerTask end(Future<Boolean> b) throws Exception {
        super.end(b);

        // Continue with stop if the wait for quiet down was not cancelled
        return isCancelled() ? null : new MasterStopTask(getTimeout(), ms);
    }
}
