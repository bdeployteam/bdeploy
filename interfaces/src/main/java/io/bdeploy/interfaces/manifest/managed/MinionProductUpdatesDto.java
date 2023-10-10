package io.bdeploy.interfaces.manifest.managed;

import java.util.Map;

/**
 * Stores map of product names for products with newer version available on managed server
 */
public class MinionProductUpdatesDto {

    public Map<String, Boolean> newerVersionAvailable;

}
