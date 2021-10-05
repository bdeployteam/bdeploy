package io.bdeploy.launcher.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32Util;

/**
 * Native hostname lookup
 */
final class NativeHostnameResolver {

    private static final Logger log = LoggerFactory.getLogger(NativeHostnameResolver.class);

    private NativeHostnameResolver() {
    }

    /**
     * Native Interface to: http://man7.org/linux/man-pages/man2/gethostname.2.html
     */
    private interface UnixCLibrary extends Library {

        UnixCLibrary INSTANCE = Native.load("c", UnixCLibrary.class);

        public int gethostname(byte[] hostname, int bufferSize);
    }

    /**
     * @return the hostname the of the current machine
     */
    public static String getHostname() {
        if (Platform.isWindows()) { /** perform windows "computername" lookup */
            return Kernel32Util.getComputerName();
        } else { /** try to call linux native library function */
            byte[] hostnameBuffer = new byte[4097];
            int result = UnixCLibrary.INSTANCE.gethostname(hostnameBuffer, hostnameBuffer.length);
            if (result != 0) {
                log.error("Native Method call failed: gethostname");
                return null;
            }
            return Native.toString(hostnameBuffer);
        }
    }
}