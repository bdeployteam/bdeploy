package io.bdeploy.common.util;

import java.util.Arrays;
import java.util.function.Consumer;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * Utility class that simplifies writing log statements with MDC data.
 * <p>
 * The logger is typically {@link #setMdcValue initialized} with a fixed MDC value that is attached to all log calls.
 * If required {@link #log(Consumer, Object...) additional} data can be passed to each log call.
 * </p>
 * <b>Typical usage pattern:</b>
 *
 * <pre>
 *  public class MyClass {
 *      <span style="color:green">private final MdcLogger logger = new MdcLogger(MyClass.class);</span>
 *
 *      public MyClass() {
 *          <span style="color:green">logger.setMdc("green");</span>
 *      }
 *
 *      public void foo() {
 *          <span style="color:green">logger.log((l) -> l.info("Doing some work."));</span>
 *
 *          <span style="color:green">logger.log((l) -> l.info("Doing more work."),"red");</span>
 *      }
 *  }
 *
 *  The following output will be printed when a pattern layout is used:
 *       %-12.12MDC{BDEPLOY} | %-5level | %-20msg - (%F)%n
 *
 *       green       | INFO | Doing some work.  - (MyClass.java)
 *       green / red | INFO | Doing more work.  - (MyClass.java)
 * </pre>
 * <p>
 * The actual log call is delegated to a functional interface so that the line numbers and the class is preserved in the log
 * statements. As consequence it is also up the the caller to check if the desired log level is enabled.
 * </p>
 */
public class MdcLogger {

    /**
     * The key to which all MDC data is associated with.
     */
    private static final String MDC_NAME = "BDEPLOY";

    private final Logger logger;
    private Object[] mdcData = new Object[0];

    /**
     * Creates a new instance using the given logger.
     *
     * @param clazz the class used by the logger
     */
    public MdcLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    /**
     * Sets the MDC value that will be associated with all subsequent log calls.
     *
     * @param value
     *            the value to associate with the MDC key.
     */
    public void setMdcValue(Object... value) {
        this.mdcData = value;
    }

    /**
     * Writes the desired log statement. The previously configured MDC value will be set during the logger is called.
     *
     * @param writer
     *            the logger to be used to write the log statement
     */
    public void log(Consumer<Logger> writer) {
        doLog(logger, writer, mdcData);
    }

    /**
     * Writes the desired log statement. The given MDC data is added to the already defined MDC data. Typically used when there is
     * a base MDC value that should be present in all statements but in some additional values are required.
     *
     * @param writer
     *            logger to be used to write the log statement
     * @param mdcData
     *            data to associate with the MDC key
     */
    public void log(Consumer<Logger> writer, Object... mdcData) {
        Object[] unifiedMdc = Arrays.copyOf(this.mdcData, this.mdcData.length + mdcData.length);
        System.arraycopy(mdcData, 0, unifiedMdc, this.mdcData.length, mdcData.length);
        doLog(logger, writer, unifiedMdc);
    }

    /** Writes the desired log statement */
    private static void doLog(Logger logger, Consumer<Logger> writer, Object... mdcData) {
        try (CloseableThreadContext.Instance ignored = CloseableThreadContext.put(MDC_NAME, Joiner.on(" / ").join(mdcData))) {
            writer.accept(logger);
        }
    }

}
