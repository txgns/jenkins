package metanectar;

import junit.framework.TestCase;

/**
 * @author Paul Sandoz
 */
public class MasterServerNameEncoderTest extends TestCase {
    String validChars = "-.0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

    public void testNotEncoded() {
        assertEquals(MasterServerNameEncoder.VALID_CHARS, MasterServerNameEncoder.encode(MasterServerNameEncoder.VALID_CHARS));
    }

    public void testPercentEncode() {
        assertEquals("%20", MasterServerNameEncoder.encode(" "));
    }

    public void testEncodedASCIIRange() {
        char[] all = new char[128];
        for (char c = 0; c < 128; c++) {
            all[c] = c;
        }

        String s = new String(all);
        String encoded = MasterServerNameEncoder.encode(s);

        assertEquals((s.length() - validChars.length()) * 3 + validChars.length(), encoded.length());
    }

    public void testEncodedHigherRange() {
        char[] all = new char[256];
        for (char c = 0; c < 256; c++) {
            all[c] = c;
        }

        String s = new String(all);
        String encoded = MasterServerNameEncoder.encode(s);

        int lowerRange = (128 - validChars.length()) * 3;
        int higherRange = 128 * 2 * 3;
        assertEquals(lowerRange + higherRange + validChars.length(), encoded.length());
    }
}
