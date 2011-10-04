package metanectar.task;

import hudson.model.LoadStatistics;
import junit.framework.TestCase;
import metanectar.provisioning.MasterProvisioner;
import metanectar.provisioning.task.TaskQueue;
import metanectar.provisioning.task.TaskWithTimeout;

import java.util.concurrent.*;

/**
 * @author Paul Sandoz
 */
public class TaskTestBase extends TestCase {
    private ExecutorService es;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        es = Executors.newCachedThreadPool();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        es.shutdown();
    }


    abstract class SomeTask extends TaskWithTimeout {
        Exception caught;

        SomeTask(long timeout) {
            super(timeout);
        }

        public Future doStart() throws Exception {
            return es.submit(createWork());
        }

        public SomeTask end(Future f) throws Exception {
            try {
                f.get();
            } catch (Exception e) {
                caught = e;
                throw e;
            }
            return null;
        }

        public abstract Callable<Object> createWork();
    }

    Future<?> processQueue(final TaskQueue<?> queue, final long period) {
        return es.submit(new Callable<Void>() {
            public Void call() throws Exception {

                while (!queue.getQueue().isEmpty()) {
                    queue.process();
                    try {
                        Thread.sleep(period);
                    } catch (InterruptedException e) {
                    }
                }
                return null;
            }
        });
    }
}
