package io.bdeploy.ui.api.impl;

import java.util.concurrent.TimeUnit;

import org.jvnet.hk2.annotations.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A service that manages tokens and file names. Entries are automatically expired after a given time.
 */
@Service
public class DownloadTokenCache {

    /**
     * Stores token as well as the real file name that should be used.
     */
    private final Cache<String, String> token2FileName = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

    /**
     * Returns the file-name assigned with the given token
     */
    public String get(String token) {
        return token2FileName.getIfPresent(token);
    }

    /**
     * Stores the given file-name using the given token.
     */
    public void add(String token, String fileName) {
        token2FileName.put(token, fileName);
    }

    /**
     * Removes the entry associated with the given token
     *
     * @param token
     */
    public void remove(String token) {
        token2FileName.invalidate(token);
    }

}
