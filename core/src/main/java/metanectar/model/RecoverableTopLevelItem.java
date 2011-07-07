package metanectar.model;

import hudson.model.TopLevelItem;

import java.util.concurrent.Future;

/**
 * A recoverable top-level item that can recover when MN is stopped and restarted.
 * <p>
 * If MetaNectar crashes (or is stopped) when a top-level item is in a (persisted) non-stable state, then
 * when MetaNectar is re-started that top-level item needs to recover and transition to a (persisted) stable state.
 * For example, if a managed master is in the provisioning state, and MetaNectar crashes and restarts, then that
 * managed master needs to re-issue the provisioning request, since the state of previous provisioning request was lost
 * when MN crashed.
 * </p>
 * <p>
 * On initialization of MetaNectar all recoverable top-level items will be obtained and the {@link #initiateRecovery()}
 * method will be invoked on those items.
 * </p>
 *
 * @author Paul Sandoz
 */
public interface RecoverableTopLevelItem extends TopLevelItem {
    /**
     * Initiate the recovery from a non-stable state by executing the necessary, and possibly asynchronous, action(s)
     * required to move to a stable state.
     * <p>
     * If in a stable stable then this method shall perform no action.
     * </p>
     *
     * @returns Future to track recovery
     * @throws Exception if an error occurs when initiating recovery.
     */
    Future<?> initiateRecovery() throws Exception;
}
