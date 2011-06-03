package metanectar.provisioning;

import antlr.ANTLRException;
import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO this is currently a dummy class.
 *
 * @author Paul Sandoz
 * @author Stephen Connolly
 */
public class MetaNectarSlaveManager implements SlaveManager {

    private static final Logger LOGGER = Logger.getLogger(MetaNectarSlaveManager.class.getName());

    private Set<LeaseIdImpl> leases = Collections.synchronizedSet(new HashSet<LeaseIdImpl>());

    private int n;

    private List<Set<String>> hosts = Arrays.asList(Collections.singleton("foo"));

    public boolean canProvision(String labelExpression) {
        try {
            Label label = LabelExpression.parseExpression(labelExpression);
            for (Set<String> hostLabels : hosts) {
                List<LabelAtom> labels = asLabelAtoms(hostLabels);
                if (label.matches(labels)) {
                    return true;
                }
            }
            return false;
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our
            // problem)
            return false;
        }
    }

    public Collection<String> getLabels() {
        HashSet<String> result = new HashSet<String>();
        for (Set<String> hostLabels : hosts) {
            result.addAll(hostLabels);
        }
        return result;
    }

    public FutureComputerLauncherFactory provision(final String labelExpression, final TaskListener listener,
                                                   final int numOfExecutors) throws ProvisioningException {
        LOGGER.log(Level.INFO, "Request: provision slave matching label expression \"{0}\" with {1} executors",
                new Object[]{labelExpression, numOfExecutors});
        try {
            Label label = LabelExpression.parseExpression(labelExpression);
            for (Set<String> hostLabels : hosts) {
                if (label.matches(asLabelAtoms(hostLabels))) {
                    LeaseIdImpl leaseId = new LeaseIdImpl(UUID.randomUUID().toString());
                    LOGGER.log(Level.INFO, "Response: provisioning as locally forked slave: {0}", leaseId);
                    listener.getLogger().println("MN: Provisioning locally forked slave id:" + leaseId.getUuid());
                    FutureComputerLauncherFactory result = new FutureComputerLauncherFactory(
                            "Locally forked slave",
                            numOfExecutors,
                            new LocalForkingComputerLauncherFactory(
                                    leaseId, "slave-" + leaseId.getUuid(),
                                    numOfExecutors,
                                    asSpaceSeparatedString(hostLabels)
                            )
                    );
                    leases.add(leaseId);
                    return result;
                }
            }
            LOGGER.log(Level.INFO, "Response: cannot provision requested slave");
            // cannot provision that label expression from our set of templates
            return null;
        } catch (ANTLRException e) {
            LOGGER.log(Level.WARNING, "Response: could not parse label expression", e);
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our
            // problem)
            return null;
        }
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        if (leases.remove(allocatedSlave.getLeaseId())) {
            LOGGER.log(Level.INFO, "Released previously provisioned slave: {0}", allocatedSlave.getLeaseId());
        }
    }

    public boolean isProvisioned(LeaseId id) {
        return id instanceof LeaseIdImpl && leases.contains(id);
    }

    private static String asSpaceSeparatedString(Set<String> hostLabels) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (String hostLabel : hostLabels) {
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
        for (String hostLabel : hostLabels) {
            labels.add(new LabelAtom(hostLabel));
        }
        return labels;
    }

}

class LeaseIdImpl implements LeaseId {
    private final String uuid;

    protected LeaseIdImpl() {
        this(null);
    }

    public LeaseIdImpl(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LeaseIdImpl leaseId = (LeaseIdImpl) o;

        if (uuid != null ? !uuid.equals(leaseId.uuid) : leaseId.uuid != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "LeaseId{" +
                "uuid='" + uuid + '\'' +
                '}';
    }

}

