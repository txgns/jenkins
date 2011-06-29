package metanectar.provisioning.task;

import junit.framework.TestCase;

import java.util.concurrent.*;

/**
 * @author Paul Sandoz
 */
public class TaskTimeoutTest extends TestCase {
    ExecutorService es = Executors.newFixedThreadPool(1);

    abstract class SomeTask extends FutureTask<Object, SomeTask>  {
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


    class WorkTask extends SomeTask {

        WorkTask(long timeout) {
            super(timeout);
        }

        public Callable<Object> createWork() {
            return new Callable<Object>() {
                public Object call() throws Exception {
                    double i = Math.PI;
                    while (true) {
                        i = i * Math.PI;
                    }
                }
            };
        }
    }

    public void testWorkTask() throws Exception {
        _testTask(new WorkTask(100));
    }


    class SleepTask extends SomeTask {

        SleepTask(long timeout) {
            super(timeout);
        }

        public Callable<Object> createWork() {
            return new Callable<Object>() {
                public Object call() throws Exception {
                    Thread.currentThread().sleep(TimeUnit.MINUTES.toMillis(1));
                    return null;
                }
            };
        }
    }

    public void testSleepTask() throws Exception {
        _testTask(new SleepTask(100));
    }


    private void _testTask(SomeTask t) throws Exception {
        TaskQueue q = new TaskQueue();
        q.add(t);

        q.process();

        Thread.currentThread().sleep(200);

        q.process();

        assertNotNull(t.caught);
        assertTrue(t.caught instanceof CancellationException);

        es.shutdown();
    }

}
