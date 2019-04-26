package io.bdeploy.common;

import javax.ws.rs.sse.SseEventSource;

public interface RemoteClientFactory extends AutoCloseable {

    public <T> T getProxyClient(Class<T> clazz, Object... additionalRegistrations);

    public SseEventSource getEventSource(String path);

    @Override
    public void close(); // don't throw

}
