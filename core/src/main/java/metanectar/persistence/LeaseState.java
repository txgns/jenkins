package metanectar.persistence;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
* Created by IntelliJ IDEA.
* User: stephenc
* Date: 20/10/2011
* Time: 16:26
* To change this template use File | Settings | File Templates.
*/
public enum LeaseState {
    /**
     * Initial state for a lease.
     */
    REQUESTED(1) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(PLANNED, DECOMMISSIONED);
        }
    },
    /**
     * We have got a {@link hudson.slaves.NodeProvisioner.PlannedNode}
     */
    PLANNED(25) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(AVAILABLE, DECOMMISSIONED);
        }
    },
    /**
     * The {@link hudson.slaves.NodeProvisioner.PlannedNode} was realized.
     */
    AVAILABLE(50) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(LEASED, DECOMMISSIONING);
        }
    },
    /**
     * The node has been leased out.
     */
    LEASED(55) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(RETURNED);
        }
    },
    /**
     * The node has been returned from lease.
     */
    RETURNED(60) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(AVAILABLE, DECOMMISSIONING);
        }
    },
    /**
     * The node is being decommissioned.
     */
    DECOMMISSIONING(90) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return set(DECOMMISSIONED);
        }
    },
    /**
     * The node has been decommissioned.
     */
    DECOMMISSIONED(100) {
        /** {@inheritDoc} */
        @Override
        @NonNull
        public Set<LeaseState> validTransitions() {
            return Collections.emptySet();
        }
    };

    private final int statusCode;

    LeaseState(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Returns the set of valid states that this state can transition to.
     *
     * @return the set of valid states that this state can transition to.
     */
    @NonNull
    public abstract Set<LeaseState> validTransitions();

    @NonNull
    private static Set<LeaseState> set(LeaseState... states) {
        return new HashSet<LeaseState>(Arrays.asList(states));
    }

    public int toStatusCode() {
        return statusCode;
    }

    @CheckForNull
    public static LeaseState fromStatusCode(int statusCode) {
        for (LeaseState state : values()) {
            if (state.statusCode == statusCode) {
                return state;
            }
        }
        return null;
    }
}
