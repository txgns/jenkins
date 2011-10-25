package metanectar.persistence;

import metanectar.test.MetaNectarRule;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static metanectar.persistence.LeaseState.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class SlaveLeaseTableTest {

    @ClassRule
    public static MetaNectarRule m = new MetaNectarRule();

    private String owner;
    private Set<String> ids = new HashSet<String>();

    private String generateUID() {
        String leaseId = UIDTable.generate();
        ids.add(leaseId);
        return leaseId;
    }

    @Before
    public void setUp() throws Exception {
        owner = generateUID();
    }

    @After
    public void tearDown() throws Exception {
        if (owner != null) {
            SlaveLeaseTable.dropOwner(owner);
        }
        for (String id: ids) {
            UIDTable.drop(id);
        }
    }

    @Test
    public void shortLifecycle_getStatus_leaseId() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getStatus(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(REQUESTED));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(REQUESTED));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(DECOMMISSIONED));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), nullValue());
    }

    @Test
    public void shortLifecycle_getOwnerLeaseId() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getOwner(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), nullValue());
    }

    @Test
    public void shortLifecycle_getTenantLeaseId() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());
    }

    @Test
    public void shortLifecycle_getLeases() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeases(), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(), not(hasItem(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeases_status() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeases(REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeases_owner() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeases(owner), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(""), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(""), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(""), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(""), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(""), not(hasItem(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeases_ownerStatus() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));
    }

    @Test
    public void shortLifecycle_getOwners() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getOwners(), not(hasItem(owner)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwners(), not(hasItem(owner)));
    }

    @Test
    public void shortLifecycle_getTenantLeases() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getTenantLeases("foo"), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases("foo"), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getTenantLeases("foo"), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases("foo"), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases("foo"), not(hasItem(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeaseRecords_status() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeaseRecords_owner() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(owner), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(""), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(""), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(""), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(""), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(""), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void shortLifecycle_getLeaseRecords_ownerStatus() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void shortLifecycle_getTenantLeaseRecords() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getTenantLeaseRecords("foo"), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords("foo"), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(false));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords("foo"), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords("foo"), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords("foo"), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void fullLifecycle_getStatus_leaseId() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getStatus(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(REQUESTED));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(PLANNED));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(AVAILABLE));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(LEASED));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(RETURNED));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(DECOMMISSIONING));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(DECOMMISSIONED));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), nullValue());
    }

    @Test
    public void fullLifecycle_getOwner_leaseId() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getOwner(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), is(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwner(leaseId), nullValue());
    }

    @Test
    public void fullLifecycle_getTenant_leaseId() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), is(tenant));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenant(leaseId), nullValue());
    }

    @Test
    public void fullLifecycle_getLeases() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeases(), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(), not(hasItem(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeases_status() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeases(REQUESTED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(PLANNED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeases(REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(PLANNED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(AVAILABLE), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeases(PLANNED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(AVAILABLE), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(LEASED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeases(AVAILABLE), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(LEASED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(RETURNED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(LEASED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(RETURNED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONING), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeases(RETURNED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONING), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONING), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(DECOMMISSIONED), not(hasItem(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeases_owner() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeases(owner), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner), not(hasItem(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeases_ownerStatus() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, PLANNED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, REQUESTED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, PLANNED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, AVAILABLE), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, PLANNED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, AVAILABLE), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, LEASED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, AVAILABLE), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, LEASED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, RETURNED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, LEASED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, RETURNED), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONING), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, RETURNED), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONING), hasItem(leaseId));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONING), not(hasItem(leaseId)));
        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), hasItem(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeases(owner, DECOMMISSIONED), not(hasItem(leaseId)));
    }

    @Test
    public void fullLifecycle_getOwners() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getOwners(), not(hasItem(owner)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getOwners(), hasItem(owner));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getOwners(), not(hasItem(owner)));
    }

    @Test
    public void fullLifecycle_getTenantLeases() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), hasItem(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeases(tenant), not(hasItem(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeaseRecords() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeaseRecords_status() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(PLANNED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(PLANNED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(AVAILABLE), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(PLANNED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(AVAILABLE), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(LEASED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(AVAILABLE), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(LEASED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(RETURNED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(LEASED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(RETURNED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONING), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(RETURNED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONING), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONING), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(DECOMMISSIONED), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeaseRecords_owner() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(owner), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void fullLifecycle_getLeaseRecords_ownerStatus() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, PLANNED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, REQUESTED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, PLANNED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, AVAILABLE), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, PLANNED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, AVAILABLE), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, LEASED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, AVAILABLE), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, LEASED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, RETURNED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, LEASED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, RETURNED), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONING), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, RETURNED), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONING), hasLeaseRecord(leaseId));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONING), not(hasLeaseRecord(leaseId)));
        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getLeaseRecords(owner, DECOMMISSIONED), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void fullLifecycle_getTenantLeaseRecords() throws Exception {
        String leaseId = generateUID();
        String tenant = generateUID();
        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, PLANNED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, PLANNED, AVAILABLE), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.registerLease(leaseId, tenant), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), hasLeaseRecord(leaseId));

        assertThat(SlaveLeaseTable.returnLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, RETURNED, DECOMMISSIONING), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.updateState(leaseId, DECOMMISSIONING, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getTenantLeaseRecords(tenant), not(hasLeaseRecord(leaseId)));
    }

    @Test
    public void getSetClearResource() throws Exception {
        String leaseId = generateUID();
        assertThat(SlaveLeaseTable.getResource(leaseId), nullValue());

        assertThat(SlaveLeaseTable.registerRequest(owner, leaseId), is(true));

        assertThat(SlaveLeaseTable.getResource(leaseId), nullValue());

        assertThat(SlaveLeaseTable.setResource(leaseId, new byte[]{1,2,3,4}), is(true));

        assertThat(SlaveLeaseTable.getResource(leaseId), is(new byte[]{1,2,3,4}));

        assertThat(SlaveLeaseTable.clearResource(leaseId), is(true));

        assertThat(SlaveLeaseTable.getResource(leaseId), nullValue());

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(REQUESTED));

        assertThat(SlaveLeaseTable.updateState(leaseId, REQUESTED, DECOMMISSIONED), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), is(DECOMMISSIONED));

        assertThat(SlaveLeaseTable.decommissionLease(leaseId), is(true));

        assertThat(SlaveLeaseTable.getStatus(leaseId), nullValue());
    }

    public static Matcher<Collection<LeaseRecord>> hasLeaseRecord(final String leaseId) {
        return new BaseMatcher<Collection<LeaseRecord>>() {
            public boolean matches(Object item) {
                Collection<LeaseRecord> collection = (Collection<LeaseRecord>) item;
                for (LeaseRecord record: collection) {
                    if (leaseId.equals(record.getLeaseId())) return true;
                }
                return false;
            }

            public void describeTo(Description description) {
                description.appendText(" a collection of LeaseRecords containing one with leaseId ");
                description.appendValue(leaseId);
            }
        };
    }

}
