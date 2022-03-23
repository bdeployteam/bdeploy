package io.bdeploy.pcu;

/**
 * Helper class working with process handles
 */
class ProcessHandles {

    private ProcessHandles() {
    }

    /** Destroys this process and all its descendants */
    static void destroy(ProcessHandle process) {
        // First kill all processes that might be forked by the root
        process.descendants().forEach(ProcessHandle::destroy);

        // Terminate the process itself
        process.destroy();
    }

}
