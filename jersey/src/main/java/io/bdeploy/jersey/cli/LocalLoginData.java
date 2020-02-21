package io.bdeploy.jersey.cli;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Locally stored data for login sessions.
 */
public class LocalLoginData {

    public SortedMap<String, LocalLoginServer> servers = new TreeMap<>();

    public String current;

}
