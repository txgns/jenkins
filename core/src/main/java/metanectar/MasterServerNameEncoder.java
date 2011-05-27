package metanectar;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * An encode that encodes a master name such that it is compatible with that name being used for a directory name.
 * <p>
 * The valid characters are in the the set [0-9A-Za-z\-.]
 * <p>
 * Any non-valid character is encoded using the percent encoding rule for URL encoding except that '_' is
 * substituted for '%'.
 *
 * @author Paul Sandoz
 */
public class MasterServerNameEncoder {

    public static final String VALID_CHARS = "-.0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final boolean[] charMap = new boolean[128];
    static {
        for (int i = 0; i < 128; i++) {
            charMap[i] = VALID_CHARS.indexOf(i) == -1;
        }
    }

    public static String encode(String s) {
        boolean escaped = false;
        StringBuilder out = null;
        CharsetEncoder enc = null;
        CharBuffer buf = null;
        char c;
        for (int i = 0, m = s.length(); i < m; i++) {
            c = s.charAt(i);
            if (c > 127 || charMap[c]) {
                if (!escaped) {
                    out = new StringBuilder(i + (m - i) * 3);
                    out.append(s.substring(0, i));
                    enc = Charset.forName("UTF-8").newEncoder();
                    buf = CharBuffer.allocate(1);
                    escaped = true;
                }
                // 1 char -> UTF8
                buf.put(0,c);
                buf.rewind();
                try {
                    ByteBuffer bytes = enc.encode(buf);
                    while (bytes.hasRemaining()) {
                        byte b = bytes.get();
                        out.append('_');
                        out.append(toDigit((b >> 4) & 0xF));
                        out.append(toDigit(b & 0xF));
                    }
                } catch (CharacterCodingException ex) { }
            } else if (escaped) {
                out.append(c);
            }
        }
        return escaped ? out.toString() : s;
    }

    private static char toDigit(int n) {
        return (char)(n < 10 ? '0' + n : 'A' + n - 10);
    }

}
