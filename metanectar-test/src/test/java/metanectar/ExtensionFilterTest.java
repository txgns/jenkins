package metanectar;

import com.google.common.collect.Lists;
import hudson.model.Descriptor;
import metanectar.provisioning.ConfiguredCommandMasterProvisioningService;
import metanectar.provisioning.MasterProvisioningService;
import metanectar.test.MetaNectarTestCase;

import java.util.Collections;

/**
 * @author Paul Sandoz
 */
public class ExtensionFilterTest extends MetaNectarTestCase {
    public void testRuleOnExtensionList() {
        assertFalse(metaNectar.getDescriptorList(MasterProvisioningService.class).isEmpty());

        ExtensionFilter ef = new ExtensionFilter(metaNectar);
        ef.rule(MasterProvisioningService.class, new ExtensionFilter.WhiteListRule(Collections.<Class>emptyList()));
        ef.filter();

        assertTrue(metaNectar.getDescriptorList(MasterProvisioningService.class).isEmpty());

        for (Descriptor d : metaNectar.getExtensionList(Descriptor.class)) {
            assertFalse(String.format("Descriptor class %s is assignable to %s", d.clazz.getName(), MasterProvisioningService.class.getName()),
                    MasterProvisioningService.class.isAssignableFrom(d.clazz));
        }
    }

    public void testRuleOnDescriptor() {
        assertNotNull(metaNectar.getDescriptorList(MasterProvisioningService.class).find(ConfiguredCommandMasterProvisioningService.class));
        assertNotNull(metaNectar.getExtensionList(Descriptor.class).get(ConfiguredCommandMasterProvisioningService.DescriptorImpl.class));

        ExtensionFilter ef = new ExtensionFilter(metaNectar);
        ef.rule(Descriptor.class, new ExtensionFilter.BlackListRule(Lists.<Class>newArrayList(
                ConfiguredCommandMasterProvisioningService.class)));
        ef.filter();

        assertNull(metaNectar.getDescriptorList(MasterProvisioningService.class).find(ConfiguredCommandMasterProvisioningService.class));
        assertNull(metaNectar.getExtensionList(Descriptor.class).get(ConfiguredCommandMasterProvisioningService.DescriptorImpl.class));
    }
}
