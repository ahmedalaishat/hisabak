import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.*;

// Byte-for-byte replica of androidApp's AesGcmBackupCrypto (same JDK APIs).
public class JvmCrypto {
    static final byte[] MAGIC = {'H','S','B','K'};
    static final int FORMAT = 2, SALT_LEN = 16, IV_LEN = 12, HEADER_LEN = 4+1+4+16+12,
                     TAG_BITS = 128, KEY_BITS = 256, ITER = 210_000;

    public static void main(String[] args) throws Exception {
        String mode = args[0], pass = args[1];
        byte[] input = Files.readAllBytes(Path.of(args[2]));
        byte[] out = mode.equals("enc") ? encrypt(input, pass) : decrypt(input, pass);
        Files.write(Path.of(args[3]), out);
        System.out.println(mode + " ok, " + out.length + " bytes");
    }

    static byte[] encrypt(byte[] plain, String pass) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT_LEN]; rnd.nextBytes(salt);
        byte[] iv = new byte[IV_LEN]; rnd.nextBytes(iv);
        byte[] header = ByteBuffer.allocate(HEADER_LEN).put(MAGIC).put((byte)FORMAT)
            .putInt(ITER).put(salt).put(iv).array();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt, ITER), new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(header);
        byte[] body = c.doFinal(plain);
        return ByteBuffer.allocate(header.length + body.length).put(header).put(body).array();
    }

    static byte[] decrypt(byte[] ct, String pass) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(ct);
        byte[] magic = new byte[4]; b.get(magic);
        int format = b.get();
        int iter = b.getInt();
        byte[] salt = new byte[SALT_LEN]; b.get(salt);
        byte[] iv = new byte[IV_LEN]; b.get(iv);
        byte[] body = new byte[b.remaining()]; b.get(body);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(pass, salt, iter), new GCMParameterSpec(TAG_BITS, iv));
        if (format == FORMAT) c.updateAAD(java.util.Arrays.copyOf(ct, HEADER_LEN));
        return c.doFinal(body);
    }

    static SecretKeySpec key(String pass, byte[] salt, int iter) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iter, KEY_BITS);
        return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).getEncoded(), "AES");
    }
}
