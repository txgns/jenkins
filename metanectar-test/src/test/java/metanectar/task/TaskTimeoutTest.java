package metanectar.task;

import metanectar.provisioning.task.TaskQueue;

import java.util.concurrent.*;

/**
 * @author Paul Sandoz
 */
public class TaskTimeoutTest extends TaskTestBase {

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

    public void testSleepTask() throws Exception {
        _testTask(new SleepTask(100));
    }

    private void _testTask(SomeTask t) throws Exception {
        TaskQueue q = new TaskQueue();
        Future<?> ft = q.add(t);

        Future<?> fq = processQueue(q, 200);

        fq.get(1, TimeUnit.MINUTES);

        assertNotNull(t.caught);
        assertTrue(t.caught instanceof CancellationException);

        assertTrue(ft.isCancelled());
        Exception caught = null;
        try {
            ft.get();
        } catch(Exception e) {
            caught = e;
        }

        assertNull(caught);
    }

}
