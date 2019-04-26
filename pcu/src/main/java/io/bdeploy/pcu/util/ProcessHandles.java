package io.bdeploy.pcu.util;

/**
 * Helper class working with process handles
 */
public class ProcessHandles {

    /** Destroys this process and all its descendants */
    public static void destroy(ProcessHandle process) throws Exception {
        // First kill all processes that might be forked by the root
        process.descendants().forEach(ph -> {
            ph.destroy();
        });
        // Terminate the process itself
        process.destroy();
    }

}
