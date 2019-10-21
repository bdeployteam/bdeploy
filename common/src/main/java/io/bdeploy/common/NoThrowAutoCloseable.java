package io.bdeploy.common;

/**
 * An {@link AutoCloseable} which does not throw on close.
 */
@FunctionalInterface
public interface NoThrowAutoCloseable extends AutoCloseable {

    @Override
    public void close();

}
