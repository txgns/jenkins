package nectar;

import hudson.util.TextFile;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.Recipe;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Run with a valid Nectar license.
 *
 * <p>
 * The downside of this is that it fixes instance ID. Among other things, if two instances have the same ID,
 * they may end up killing each other's builds.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(WithLicense.RunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface WithLicense {
    public class RunnerImpl extends Recipe.Runner<WithLicense> {
        @Override
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
            createLicense(home);
            createSecretKey(home);
        }

        private void createLicense(File home) throws Exception {
            String cert = IOUtils.toString(HudsonTestCase.class.getClassLoader().getResourceAsStream("cert.10.year"));
            String lk = IOUtils.toString(HudsonTestCase.class.getClassLoader().getResourceAsStream("license.key"));
            TextFile f = new TextFile(new File(home, "license.xml"));
            StringBuilder b = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?>\n");
            b.append("<hudson.license.LicenseManager>\n");
            b.append("<key>\n");
            b.append(lk);
            b.append("\n</key>\n");
            b.append("<certificate>\n");
            b.append(cert);
            b.append("\n</certificate>");
            b.append("\n</hudson.license.LicenseManager>");
            f.write(b.toString());
        }

        private void createSecretKey(File home) throws IOException {
            TextFile secretFile = new TextFile(new File(home,"secret.key"));
            if(secretFile.exists())
                secretFile.delete();
            secretFile.write("cafebabe"); //"cafebabe" is the fixed secret key
        }
    }
}
