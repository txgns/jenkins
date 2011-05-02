package metanectar.provisioning.task;

/**
 * @author Paul Sandoz
 */
public interface Task<T extends Task> {

    boolean isStarted();

    void start() throws Exception;

    boolean isDone();

    T end() throws Exception;
}
