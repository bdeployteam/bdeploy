package io.bdeploy.minion.user;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * Hash passwords for storage, and test passwords against password tokens.
 * Instances of this class can be used concurrently by multiple threads.
 *
 * @author erickson
 * @see <a href="http://stackoverflow.com/a/2861125/3474">StackOverflow</a>
 */
public final class PasswordAuthentication implements Authenticator {

    /**
     * Each token produced by this class uses this identifier as a prefix.
     */
    private static final String ID = "$31$";

    /**
     * The minimum recommended cost, used by default
     */
    private static final int DEFAULT_COST = 16;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int MIN_SIZE = 12;
    private static final int MAX_SIZE = 128;
    private static final Pattern layout = Pattern.compile("\\$31\\$(\\d\\d?)\\$(.{43})");
    private static final SecureRandom random = new SecureRandom();

    private static int iterations(int cost) {
        if ((cost < 0) || (cost > 30)) {
            throw new IllegalArgumentException("cost: " + cost);
        }
        return 1 << cost;
    }

    /**
     * Validates the given password and throws a {@link WebApplicationException} if it is invalid.
     * <p>
     * If the check fails, the password array is immediately filled with zeros (before throwing) in order remove the plain-text
     * password from memory ASAP.
     *
     * @param password The password to validate
     */
    public static void throwIfPasswordInvalid(char[] password) {
        if (password.length < MIN_SIZE) {
            Arrays.fill(password, '\0'); // Immediately remove from memory
            throw new WebApplicationException("Password too short. Minimum: " + MIN_SIZE + " characters.",
                    Status.EXPECTATION_FAILED);
        }
        if (password.length > MAX_SIZE) {
            Arrays.fill(password, '\0'); // Immediately remove from memory
            throw new WebApplicationException("Password too long. Maximum: " + MAX_SIZE + " characters.",
                    Status.EXPECTATION_FAILED);
        }
    }

    /**
     * Hashes a password for storage.
     * <p>
     * Fills the password-array with zeros in order to remove it from memory ASAP.
     *
     * @return a secure authentication token to be stored for later authentication
     */
    public static String hash(char[] password) {
        throwIfPasswordInvalid(password);

        byte[] salt = new byte[MAX_SIZE / 8];
        random.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, 1 << DEFAULT_COST);
        byte[] hash = new byte[salt.length + dk.length];
        System.arraycopy(salt, 0, hash, 0, salt.length);
        System.arraycopy(dk, 0, hash, salt.length, dk.length);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return ID + DEFAULT_COST + '$' + enc.encodeToString(hash);
    }

    /**
     * Authenticate with a password and a stored password token.
     * <p>
     * Fills the password-array with zeros in order to remove it from memory ASAP.
     *
     * @return <code>true</code> if the password and token match, else <code>false</code>
     */
    private static boolean verify(char[] password, String token) {
        Matcher m = layout.matcher(token);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid token format");
        }
        int iterations = iterations(Integer.parseInt(m.group(1)));
        byte[] hash = Base64.getUrlDecoder().decode(m.group(2));
        byte[] salt = Arrays.copyOfRange(hash, 0, MAX_SIZE / 8);
        byte[] check = pbkdf2(password, salt, iterations);
        int zero = 0;
        for (int idx = 0; idx < check.length; ++idx) {
            zero |= hash[salt.length + idx] ^ check[idx];
        }
        return zero == 0;
    }

    /**
     * Fills the password with zeros after using it to remove it from memory ASAP
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, MAX_SIZE);
        Arrays.fill(password, '\0'); // Immediately remove from memory
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGORITHM);
            return f.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Missing algorithm: " + ALGORITHM, ex);
        } catch (InvalidKeySpecException ex) {
            throw new IllegalStateException("Invalid SecretKeyFactory", ex);
        }
    }

    @Override
    public UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        if (verify(password, user.password)) {
            trace.log("password successfully verified");
            return user;
        }
        trace.log("password verification failed");
        return null;
    }

    @Override
    public UserInfo getInitialInfo(String username, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        Arrays.fill(password, '\0'); // Immediately remove from memory
        trace.log("  query not supported");
        return null; // not supported
    }

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        if (settings.disableBasic) {
            return false;
        }
        return !user.external;
    }

    @Override
    public boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings) {
        return !user.inactive;
    }
}
