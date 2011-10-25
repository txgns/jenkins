package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.cloudbees.commons.metanectar.utils.NamedThreadFactory;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import metanectar.model.ConnectedMaster;
import metanectar.model.SlaveTrader;
import metanectar.persistence.Datastore;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

    private final ConnectedMaster tenant;

    private final ExecutorService threadPool =
            new ThreadPoolExecutor(0, 5,
                    5, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    new ExceptionCatchingThreadFactory(
                            new DaemonThreadFactory(new NamedThreadFactory(ScopedSlaveManager.class.getName()))
                    )
            );

    public ScopedSlaveManager(ItemGroup<? extends Item> scope, ConnectedMaster tenant) {
        scope.getClass();
        this.scope = scope;
        this.tenant = tenant;
    }

    public boolean canProvision(String labelExpression) {
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            LOGGER.log(Level.FINE, "{0}[{1}].canProvision({2})?",
                    new Object[]{ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression});
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveTrader) {
                    final SlaveTrader trader = (SlaveTrader) item;
                    if (trader.canProvision(labelExpression)) {
                        LOGGER.log(Level.INFO, "{0}[{1}].canProvision({2})={3}",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(),
                                        item.getUrl(),
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

    public FutureComputerLauncherFactory provision(final String labelExpression, final TaskListener listener, final int numOfExecutors)
            throws ProvisioningException {
        LOGGER.log(Level.INFO, "{0}[{1}].provision({2})",
                new Object[]{ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression});
        String displayName = scope.getUrl() + "[#="+numOfExecutors+",label=" + labelExpression + "]";
        return new FutureComputerLauncherFactory(displayName, numOfExecutors, threadPool.submit(
                new Callable<ComputerLauncherFactory> () {
                    public ComputerLauncherFactory call() throws Exception {
                        ItemGroup<? extends Item> scope = ScopedSlaveManager.this.scope;
                        while (scope != null) {
                            for (Item item : scope.getItems()) {
                                if (item instanceof SlaveTrader) {
                                    final SlaveTrader slaveTrader = (SlaveTrader) item;
                                    if (slaveTrader.canProvision(labelExpression)) {
                                        LOGGER.log(Level.INFO, "{0}[{1}].provision({2}) -> {3}",
                                                new Object[]{
                                                        ScopedSlaveManager.class.getSimpleName(), scope.getUrl(), labelExpression,
                                                        item.getUrl()
                                                });

                                        try {
                                            return slaveTrader.provision(tenant.getUid(), labelExpression, listener, numOfExecutors).
                                                        get();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();  //To change body of catch statement use File |
                                            // Settings | File Templates.
                                        } catch (ExecutionException e) {
                                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                        } catch (ProvisioningException e) {
                                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                        }
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
                                new Object[]{ScopedSlaveManager.class.getSimpleName(), ScopedSlaveManager.this.scope.getUrl(), labelExpression, null});
                        throw new ProvisioningException("No resources available");
                    }
                }));
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        ItemGroup<? extends Item> scope = this.scope;
        while (scope != null) {
            for (Item item : scope.getItems()) {
                if (item instanceof SlaveTrader) {
                    final SlaveTrader trader = (SlaveTrader) item;
                    boolean provisioned = trader.isProvisioned(allocatedSlave.getLeaseId());
                    LOGGER.log(Level.INFO, "{0}[{1}]->[{3}].isProvisioned({2}) -> {4}",
                            new Object[]{
                                    ScopedSlaveManager.class.getSimpleName(), scope.getUrl(),
                                    allocatedSlave.getLeaseId(), item.getUrl(), provisioned
                            });
                    if (provisioned) {
                        LOGGER.log(Level.INFO, "{0}[{1}].release({2}) -> {3}",
                                new Object[]{
                                        ScopedSlaveManager.class.getSimpleName(), scope.getUrl(),
                                        allocatedSlave.getLeaseId(), item.getUrl()
                                });
                        trader.release(allocatedSlave);
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

    /**
     * Famous zombie hunter who will stop at nothing to ensure that all zombies are killed.
     */
    @Extension
    public static class ToshioTamura extends PeriodicWork {

        /**
         * The zombies we have to hunt.
         */
        private final ConcurrentSkipListSet<Zombie> zombies = new ConcurrentSkipListSet<Zombie>();

        /**
         * A zombie.
         */
        private static final class Zombie {
            /**
             * The manager that owns the
             */
            private final WeakReference<SlaveManager> manager;
            private final LeaseId leaseId;
            private final FutureComputerLauncherFactory futureFactory;

            private Zombie(SlaveManager manager, LeaseId leaseId,
                           FutureComputerLauncherFactory futureFactory) {
                this.manager = new WeakReference<SlaveManager>(manager);
                this.leaseId = leaseId;
                this.futureFactory = futureFactory;
            }

            public WeakReference<SlaveManager> getManager() {
                return manager;
            }

            public LeaseId getLeaseId() {
                return leaseId;
            }

            public FutureComputerLauncherFactory getFutureFactory() {
                return futureFactory;
            }

            public boolean isDead() {
                SlaveManager slaveManager = manager.get();
                if (slaveManager == null) {
                    // nobody to return the lease to
                    return true;
                }
                if (!slaveManager.isProvisioned(leaseId)) {
                    // no longer leased, so we're done
                    return true;
                }
                if (!futureFactory.isDone()) {
                    // poke it again
                    futureFactory.cancel(true);
                }
                if (futureFactory.isDone()) {
                    try {
                        ComputerLauncherFactory computerLauncherFactory = futureFactory.get();
                        slaveManager.release(computerLauncherFactory);
                        return true;
                    } catch (InterruptedException e) {
                        // should never happen as it's completed already
                    } catch (ExecutionException e) {
                        // didn't provision, so we're done
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(10);
        }

        @Override
        protected void doRun() throws Exception {
            Datastore.getDataSource();
            for (Iterator<Zombie> i = zombies.iterator(); i.hasNext();) {
                Zombie zombie = i.next();
                if (zombie.isDead()) {
                    i.remove();
                }
            }
        }

        /**
         * Keep tabs on the specified future for the lease from the slave manager, and ensure that if it is a zombie
         * it gets killed.
         * @param manager the slave manager.
         * @param leaseId the lease id.
         * @param futureFactory the possibly zombie future.
         */
        public static void hunt(SlaveManager manager, LeaseId leaseId,
                           FutureComputerLauncherFactory futureFactory) {
            Zombie zombie = new Zombie(manager, leaseId, futureFactory);
            if (!zombie.isDead()) {
                ToshioTamura instance =
                        Hudson.getInstance().getExtensionList(PeriodicWork.class)
                                .get(ToshioTamura.class);
                if (instance != null) {
                    instance.zombies.add(zombie);
                }
            }
        }
    }
}
