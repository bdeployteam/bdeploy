/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.common.util;

/**
 * Helper to convert checksums in byte[] representation to a {@link String}
 */
public class Hex {

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
