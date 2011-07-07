package metanectar.provisioning.task;

import hudson.model.LoadStatistics;
import junit.framework.TestCase;
import metanectar.provisioning.MasterProvisioner;

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


    abstract class SomeTask extends TaskWithFuture {
        Exception caught;

        SomeTask(long timeout) {
            super(timeout);
        }

        public void start() throws Exception {
            setFuture(es.submit(createWork()));
        }

        public SomeTask end() throws Exception {
            try {
                getFuture().get();
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
                        Thread.currentThread().sleep(period);
                    } catch (InterruptedException e) {
                    }
                }
                return null;
            }
        });
    }
}
