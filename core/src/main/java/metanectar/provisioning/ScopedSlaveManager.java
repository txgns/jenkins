package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link SlaveManager} that can provision slaves available within a provided scope.
 *
 * @author Stephen Connolly
 */
public class ScopedSlaveManager implements SlaveManager {
    private static final Logger LOGGER = Logger.getLogger(ScopedSlaveManager.class.getName());

    private final ItemGroup<? extends Item> scope;

    public ScopedSlaveManager(ItemGroup<? extends Item> scope) {
        scope.getClass();
        this.scope = scope;
    }

    public boolean canProvision(String labelExpression) {
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            LOGGER.log(Level.INFO, "{0}[{1}].canProvision({2})?",
                    new Object[]{ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression});
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveManager) {
                    final SlaveManager slaveManager = (SlaveManager) item;
                    if (slaveManager.canProvision(labelExpression)) {
                        LOGGER.log(Level.INFO, "{0}[{1}].canProvision({2})={3}",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(),
                                        scope.getUrl(),
                                        labelExpression,
                                        Boolean.TRUE
                                });
                        return true;
                    }
                }
            }
            if (scope instanceof Item) {
                scope = Item.class.cast(scope).getParent();
            } else {
                scope = null;
            }
        }
        LOGGER.log(Level.INFO, "{0}[{1}].canProvision({2})={3}",
                new Object[]{
                        ScopedSlaveManager.class.getSimpleName(),
                        this.scope.getUrl(),
                        labelExpression,
                        Boolean.FALSE
                });
        return false;
    }

    public Collection<String> getLabels() {
        Set<String> result = new HashSet<String>();
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveManager) {
                    final SlaveManager slaveManager = (SlaveManager) item;
                    result.addAll(slaveManager.getLabels());
                }
            }
            if (scope instanceof Item) {
                scope = Item.class.cast(scope).getParent();
            } else {
                scope = null;
            }
        }
        LOGGER.log(Level.INFO, "{0}[{1}].getLabels()={2}",
                new Object[]{ScopedSlaveManager.class.getSimpleName(), this.scope.getUrl(), result});
        return result;
    }

    public FutureComputerLauncherFactory provision(String labelExpression, TaskListener listener, int numOfExecutors)
            throws ProvisioningException {
        LOGGER.log(Level.INFO, "{0}[{1}].provision({2})",
                new Object[]{ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression});
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveManager) {
                    final SlaveManager slaveManager = (SlaveManager) item;
                    if (slaveManager.canProvision(labelExpression)) {
                        LOGGER.log(Level.INFO, "{0}[{1}].provision({2}) -> {3}",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression,
                                        item.getUrl()
                                });
                        return slaveManager.provision(labelExpression, listener, numOfExecutors);
                    }
                }
            }
            if (scope instanceof Item) {
                scope = Item.class.cast(scope).getParent();
            } else {
                scope = null;
            }
        }
        LOGGER.log(Level.INFO, "{0}[{1}].provision({2}) -> {3}",
                new Object[]{ScopedSlaveManager.class.getSimpleName(), this.scope.getUrl(), labelExpression, null});
        return null;
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveManager) {
                    final SlaveManager slaveManager = (SlaveManager) item;
                    if (slaveManager.isProvisioned(allocatedSlave.getLeaseId())) {
                        LOGGER.log(Level.INFO, "{0}[{1}].release({2}) -> {3}",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(), scope.getUrl(),
                                        allocatedSlave.getLeaseId(), item.getUrl()
                                });
                        slaveManager.release(allocatedSlave);
                        return;
                    }
                }
            }
            if (scope instanceof Item) {
                scope = Item.class.cast(scope).getParent();
            } else {
                scope = null;
            }
        }
        LOGGER.log(Level.INFO, "{0}[{1}].release({2}) -> unknown",
                new Object[]{
                        ScopedSlaveManager.class.getSimpleName(), this.scope.getUrl(),
                        allocatedSlave.getLeaseId()
                });
    }

    public boolean isProvisioned(LeaseId id) {
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveManager) {
                    final SlaveManager slaveManager = (SlaveManager) item;
                    if (slaveManager.isProvisioned(id)) {
                        LOGGER.log(Level.INFO, "{0}[{1}].isProvisioned({2}) = true [{3}]",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(), scope.getUrl(),
                                        id, item.getUrl()
                                });
                        return true;
                    }
                }
            }
            if (scope instanceof Item) {
                scope = Item.class.cast(scope).getParent();
            } else {
                scope = null;
            }
        }
        LOGGER.log(Level.INFO, "{0}[{1}].isProvisioned({2}) -> false",
                new Object[]{ScopedSlaveManager.class.getSimpleName(), this.scope.getUrl(), id});
        return false;
    }
}
