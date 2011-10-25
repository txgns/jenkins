package metanectar.persistence;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import net.jcip.annotations.Immutable;

/**
 * Details of a lease.
 */
@Immutable
public class LeaseRecord {
    @NonNull
    private final String leaseId;
    @CheckForNull
    private final String ownerId;
    @CheckForNull
    private final String tenantId;
    @NonNull
    private final LeaseState status;

    public LeaseRecord(@NonNull String leaseId, @Nullable String ownerId, @Nullable String tenantId, @NonNull LeaseState status) {
        leaseId.getClass();
        status.getClass();
        this.leaseId = leaseId;
        this.ownerId = ownerId;
        this.tenantId = tenantId;
        this.status = status;
    }

    @NonNull
    public String getLeaseId() {
        return leaseId;
    }

    @CheckForNull
    public String getOwnerId() {
        return ownerId;
    }

    @CheckForNull
    public String getTenantId() {
        return tenantId;
    }

    @NonNull
    public LeaseState getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LeaseRecord that = (LeaseRecord) o;

        if (!leaseId.equals(that.leaseId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return leaseId.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("LeaseRecord");
        sb.append("{leaseId='").append(leaseId).append('\'');
        sb.append(", ownerId='").append(ownerId).append('\'');
        sb.append(", tenantId='").append(tenantId).append('\'');
        sb.append(", status=").append(status);
        sb.append('}');
        return sb.toString();
    }
}
