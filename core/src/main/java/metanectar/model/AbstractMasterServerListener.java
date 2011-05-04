package metanectar.model;

/**
 * A listener for MasterServer related events that invokes specific methods for state changes.
 *
 * @author Paul Sandoz
 */
public abstract class AbstractMasterServerListener extends MasterServerListener {

    public void onStateChange(MasterServer ms) {
        MasterServer.State s = ms.getState();

        switch (s) {
            case Created:
                onCreated(ms);
                break;
            case PreProvisioning:
                break;
            case Provisioning:
                onProvisioning(ms);
                break;
            case ProvisioningErrorNoResources:
                onProvisioningErrorNoResources(ms);
                break;
            case ProvisioningError:
                onProvisioningError(ms);
                break;
            case Provisioned:
                onProvisioned(ms);
                break;
            case Starting:
                onStarting(ms);
                break;
            case StartingError:
                onStartingError(ms);
                break;
            case Started:
                onStarted(ms);
                break;
            case Approved:
                onApproved(ms);
                break;
            case ApprovalError:
                onApprovalError(ms);
                break;
            case Stopping:
                onStopping(ms);
                break;
            case StoppingError:
                onStoppingError(ms);
                break;
            case Stopped:
                onStopped(ms);
                break;
            case Terminating:
                onTerminating(ms);
                break;
            case TerminatingError:
                onTerminatingError(ms);
                break;
            case Terminated:
                onTerminated(ms);
                break;
        }
    }

    public void onCreated(MasterServer ms) {}

    public void onProvisioning(MasterServer ms) {}

    public void onProvisioningErrorNoResources(MasterServer ms) {}

    public void onProvisioningError(MasterServer ms) {}

    public void onProvisioned(MasterServer ms) {}

    public void onStarting(MasterServer ms) {}

    public void onStartingError(MasterServer ms) {}

    public void onStarted(MasterServer ms) {}

    public void onApproved(MasterServer ms) {}

    public void onApprovalError(MasterServer ms) {}

    public void onStopping(MasterServer ms) {}

    public void onStoppingError(MasterServer ms) {}

    public void onStopped(MasterServer ms) {}

    public void onTerminating(MasterServer ms) {}

    public void onTerminatingError(MasterServer ms) {}

    public void onTerminated(MasterServer ms) {}

    public void onConnected(MasterServer ms) {}

    public void onDisconnected(MasterServer ms) {}

}
