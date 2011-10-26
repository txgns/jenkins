package metanectar.persistence;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import net.jcip.annotations.Immutable;

import java.util.Date;
import java.util.GregorianCalendar;

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
    @CheckForNull
    private final Date lastModified;

    public LeaseRecord(@NonNull String leaseId, @Nullable String ownerId, @Nullable String tenantId,
                       @NonNull LeaseState status, @Nullable Date lastModified) {
        leaseId.getClass();
        status.getClass();
        this.leaseId = leaseId;
        this.ownerId = ownerId;
        this.tenantId = tenantId;
        this.status = status;
        this.lastModified = lastModified == null ? null : new Date(lastModified.getTime());
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

    @CheckForNull
    public Date getLastModified() {
        return lastModified == null ? null : new Date(lastModified.getTime());
    }

    /**
     * Gets the string that says how long since this record was modified.
     *
     * @return string like "3 minutes" "1 day" etc.
     */
    public String getLastModifiedString() {
        long duration = new GregorianCalendar().getTimeInMillis()-lastModified.getTime();
        return Util.getPastTimeString(duration);
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
        sb.append(", lastModified=").append(lastModified);
        sb.append('}');
        return sb.toString();
    }
}
