package metanectar;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolLocationNodeProperty;
import metanectar.model.MetaNectar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Filter extensions and descriptors according to a set of rules.
 *
 * @author Paul Sandoz
 */
public class ExtensionFilter {
    private static final Logger LOGGER = Logger.getLogger(ExtensionFilter.class.getName());

    private final MetaNectar mn;

    private final Multimap<Class, Predicate<Class>> rules;

    public ExtensionFilter(MetaNectar mn) {
        this.mn = mn;
        this.rules = HashMultimap.create();
    }

    public void filter() {
        final List<Class> removedExtensions = Lists.newArrayList();
        for (final Map.Entry<Class, Predicate<Class>> e : rules.entries()) {
            final Class extensionType = e.getKey();

            if (extensionType == Descriptor.class) {
                final ExtensionList<Descriptor> el = mn.getExtensionList(Descriptor.class);
                for (final Descriptor de : el) {
                    if (!e.getValue().apply(de.clazz)) {
                        removedExtensions.add(de.clazz);
                        el.remove(de);
                        try {
                            mn.getDescriptorList(de.getT()).remove(de);
                        } catch (IllegalStateException ex) {
                        }
                    }
                }
            } else {
                final ExtensionList el = mn.getExtensionList(extensionType);
                for (final Object o : el) {
                    if (!e.getValue().apply(o.getClass())) {
                        removedExtensions.add(o.getClass());
                        el.remove(o);
                    }
                }

                final DescriptorExtensionList<? extends Describable, ? extends Descriptor> del = mn.getDescriptorList(e.getKey());
                for (final Descriptor de : del) {
                    if (!e.getValue().apply(de.clazz)) {
                        removedExtensions.add(de.clazz);
                        del.remove(de);
                        mn.getExtensionList(Descriptor.class).remove(de);
                    }
                }
            }
        }

        if (!removedExtensions.isEmpty())
            LOGGER.info("Removed extensions " + removedExtensions);
    }

    public ExtensionFilter rule(Class extensionType, Predicate<Class> rule) {
        rules.put(extensionType, rule);
        return this;
    }

    public static class AssignableRule implements Predicate<Class> {
        private final Class assignableToClass;

        public AssignableRule(Class assignableToClass) {
            this.assignableToClass = assignableToClass;
        }

        public boolean apply(Class input) {
            return assignableToClass.isAssignableFrom(input);
        }
    }

    public static final AssignableRule METANECTAR_EXTENSION_POINT = new AssignableRule(MetaNectarExtensionPoint.class);

    public static class WhiteListRule implements Predicate<Class> {
        private final List<Class> whiteList;

        public WhiteListRule(List<Class> whiteList) {
            this.whiteList = whiteList;
        }

        public boolean apply(Class input) {
            return whiteList.contains(input);
        }
    }

    public static class BlackListRule implements Predicate<Class> {
        private final List<Class> blackList;

        public BlackListRule(List<Class> blackList) {
            this.blackList = blackList;
        }

        public boolean apply(Class input) {
            return !blackList.contains(input);
        }
    }


    private static class DefaultExtensionFilter extends ExtensionFilter {
        public DefaultExtensionFilter(MetaNectar mn) {
            super(mn);

            rule(Cloud.class, METANECTAR_EXTENSION_POINT);

            rule(NodeProperty.class, new BlackListRule(Lists.<Class>newArrayList(
                    EnvironmentVariablesNodeProperty.class, ToolLocationNodeProperty.class
            )));

            rule(ToolInstallation.class, new WhiteListRule(Collections.<Class>emptyList()));
        }
    }

    public static void defaultFilter(MetaNectar mn) {
        new DefaultExtensionFilter(mn).filter();
    }
}