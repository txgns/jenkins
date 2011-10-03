package metanectar.provisioning.task;

import hudson.model.Computer;
import metanectar.model.MasterTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class TemplateCloneTask extends TaskWithTimeout<String, TemplateCloneTask> {
    private static final Logger LOGGER = Logger.getLogger(TemplateCloneTask.class.getName());

    private final MasterTemplate mt;

    public TemplateCloneTask(long timeout, MasterTemplate mt) {
        super(timeout);
        this.mt = mt;
    }

    public Future<String> doStart() throws Exception {
        try {
            LOGGER.info("Cloning to " + getTemplateAndSourceDescription());

            mt.setCloningState();

            return Computer.threadPoolForRemoting.submit(new Callable<String>() {
                public String call() throws Exception {
                    return mt.getSource().toTemplate().getAbsolutePath();
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cloning error for " + getTemplateAndSourceDescription(), e);

            mt.setCloningErrorState(e);
            throw e;
        }
    }

    public TemplateCloneTask end(Future<String> f) throws Exception {
        try {
            final String templatePath = f.get();

            LOGGER.info("Cloning completed for " + getTemplateAndSourceDescription());

            mt.setClonedState(templatePath);
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Cloning completion error for " + getTemplateAndSourceDescription(), cause);

            mt.setCloningErrorState(cause);
            throw e;
        }

        return null;
    }

    private String getTemplateAndSourceDescription() {
        return "template " + mt.getName() + " from source " + mt.getSource().getSourceDescription();
    }
}