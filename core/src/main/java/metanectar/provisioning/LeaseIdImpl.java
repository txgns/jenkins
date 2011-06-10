package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.LeaseId;

/**
 * A unique lease identifier.
 *
 * @author Stephen Connolly
 */
public class LeaseIdImpl implements LeaseId {
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
