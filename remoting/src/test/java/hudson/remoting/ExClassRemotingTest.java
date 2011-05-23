package hudson.remoting;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExClassRemotingTest extends TestCase implements Serializable {

    private transient Thread remote;

    private transient final AtomicBoolean tearingDown = new AtomicBoolean(false);

    private transient final ExecutorService localPool = Executors.newCachedThreadPool();
    private transient Channel localChannel;

    public void setUp() throws Exception {
        PipedOutputStream toRemote = new PipedOutputStream();
        final PipedInputStream fromLocal = new PipedInputStream(toRemote);
        final PipedOutputStream toLocal = new PipedOutputStream();
        PipedInputStream fromRemote = new PipedInputStream(toLocal);

        assertTrue(getClass().getClassLoader() instanceof URLClassLoader);
        URLClassLoader loader = (URLClassLoader) getClass().getClassLoader();
        for (URL url : loader.getURLs()) {
            System.out.println(url);
        }
        ClassLoader remoteLoader = new URLClassLoader(loader.getURLs(), loader.getParent());

        remote = new Thread(new BootstrapTask(remoteLoader, fromLocal, toLocal, tearingDown));

        remote.start();
        localChannel = new Channel("outbound-channel", localPool, Channel.Mode.BINARY,
                fromRemote, toRemote, null, false, remoteLoader);

    }

    public void tearDown() throws Exception {
        tearingDown.set(true);
        System.out.println("Closing channel...");
        localChannel.close();
        System.out.println("Channel closed, waiting for remote thread to stop");
        localPool.shutdown();
        remote.join();
        System.out.println("Waiting for executor service to terminate...");
        localPool.shutdownNow();
        System.out.println("All tidy");
    }

    public void testSmokes() throws Throwable {
        System.out.println("L: got here");
        Callable<String, Throwable> callable = new Callable<String, Throwable>() {
            public String call() throws Throwable {
                System.out.println("R: got here");
                return getClass().getClassLoader().toString();
            }
        };
        assertNotSame(callable.getClass().getClassLoader().toString(), localChannel.call(callable));

    }

    public static class BootstrapTask implements Runnable {
        private final ClassLoader remoteLoader;
        private final InputStream fromLocal;
        private final OutputStream toLocal;
        private AtomicBoolean tearingDown;

        public BootstrapTask(ClassLoader remoteLoader, InputStream fromLocal, OutputStream toLocal,
                             AtomicBoolean tearingDown) {
            this.remoteLoader = remoteLoader;
            this.fromLocal = fromLocal;
            this.toLocal = toLocal;
            this.tearingDown = tearingDown;
        }

        public void run() {
            Thread.currentThread().setContextClassLoader(remoteLoader);
            try {
                Class<?> remoteClass = remoteLoader.loadClass(RemoteChannelHostTask.class.getName());
                Constructor<?> remoteConstructor = remoteClass.getConstructor(ClassLoader.class, InputStream.class,
                        OutputStream.class, AtomicBoolean.class);
                Object remoteInstance = remoteConstructor.newInstance(remoteLoader, fromLocal, toLocal, tearingDown);
                Method run = remoteClass.getMethod("run");
                run.invoke(remoteInstance);
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    public static class RemoteChannelHostTask implements Runnable {
        private final ClassLoader remoteLoader;
        private final InputStream fromLocal;
        private final OutputStream toLocal;
        private AtomicBoolean tearingDown;

        public RemoteChannelHostTask(ClassLoader remoteLoader, InputStream fromLocal, OutputStream toLocal,
                                     AtomicBoolean tearingDown) {
            this.remoteLoader = remoteLoader;
            this.fromLocal = fromLocal;
            this.toLocal = toLocal;
            this.tearingDown = tearingDown;
        }

        public void run() {
            ExecutorService remotePool = Executors.newCachedThreadPool();
            try {
                Channel remoteChannel = new Channel("inbound-channel", remotePool, Channel.Mode.BINARY,
                        fromLocal, toLocal, null, false, remoteLoader);
                try {
                    System.out.println("R: waiting for channel to close");
                    while (!tearingDown.get()) {
                        remoteChannel.join(50);
                    }
                } finally {
                    remoteChannel.close();
                    System.out.println("R: Channel to closed");
                }
            } catch (IOException e) {
                // ignore
            } catch (InterruptedException e) {
                // ignore
            } finally {
                try {
                    fromLocal.close();
                } catch (IOException e) {
                    // ignore
                }
                try {
                    toLocal.close();
                } catch (IOException e) {
                    // ignore
                }
                remotePool.shutdownNow();
            }
        }
    }
}
