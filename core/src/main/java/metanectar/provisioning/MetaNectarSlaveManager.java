package metanectar.provisioning;

import antlr.ANTLRException;
import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * TODO this is currently a dummy class.
 *
 * @author Paul Sandoz
 */
public class MetaNectarSlaveManager implements SlaveManager {
    private int n;

    private List<Set<String>> hosts = Arrays.asList(Collections.singleton("foo"));

    public boolean canProvision(String labelExpression) throws IOException, InterruptedException {
        try {
            Label label = LabelExpression.parseExpression(labelExpression);
            for (Set<String> hostLabels: hosts) {
                List<LabelAtom> labels = asLabelAtoms(hostLabels);
                if (label.matches(labels)) return true;
            }
            return false;
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our problem)
            return false;
        }
    }

    public Collection<String> getLabels() {
        HashSet<String> result = new HashSet<String>();
        for (Set<String> hostLabels: hosts) {
            result.addAll(hostLabels);
        }
        return result;
    }

    public FutureComputerLauncherFactory provision(final String labelExpression, final TaskListener listener, final int numOfExecutors) throws IOException, InterruptedException {
        try {
            Label label = LabelExpression.parseExpression(labelExpression);
            for (Set<String> hostLabels: hosts) {
                if (label.matches(asLabelAtoms(hostLabels))) {
                    listener.getLogger().println("MN: Started provisioning");
                    final String displayName = "slave" + (n++);
                    final String labels = asSpaceSeparatedString(hostLabels);
                    final Future<ComputerLauncherFactory> task = Hudson.MasterComputer.threadPoolForRemoting.submit(new Callable<ComputerLauncherFactory>() {
                        public ComputerLauncherFactory call() throws Exception {
                            listener.getLogger().println("Provisioning LocalForkingComputerLauncherFactory");
                            return new LocalForkingComputerLauncherFactory(displayName, numOfExecutors, labels);
                        }
                    });

                    return new FutureComputerLauncherFactory(displayName, 1, task);
                }
            }
            return null;
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our problem)
            return null;
        }
    }

    private static String asSpaceSeparatedString(Set<String> hostLabels) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (String hostLabel: hostLabels) {
            if (first) {
                first = false;
            } else {
                buf.append(' ');
            }
            buf.append(hostLabel);
        }
        return buf.toString();
    }

    private static List<LabelAtom> asLabelAtoms(Set<String> hostLabels) {
        List<LabelAtom> labels = new ArrayList<LabelAtom>(hostLabels.size());
        for (String hostLabel: hostLabels) {
            labels.add(new LabelAtom(hostLabel));
        }
        return labels;
    }

}
