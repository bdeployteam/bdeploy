package io.bdeploy.interfaces.manifest.statistics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ClientUsageData {

    @JsonIgnore
    private final SimpleDateFormat sdf;

    // day -> applicationId -> hostname -> count
    private final SortedMap<String, SortedMap<String, SortedMap<String, Integer>>> clientUsage = new TreeMap<>();

    public ClientUsageData() {
        sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void increment(String applicationId, String hostname) {
        String today = getToday();

        SortedMap<String, SortedMap<String, Integer>> applicationMap = clientUsage.computeIfAbsent(today, t -> new TreeMap<>());
        SortedMap<String, Integer> hostnameMap = applicationMap.computeIfAbsent(applicationId, a -> new TreeMap<>());
        hostnameMap.compute(hostname, (k, count) -> count == null ? 1 : ++count);

        while (clientUsage.size() > 30) {
            clientUsage.remove(clientUsage.firstKey());
        }
    }

    private String getToday() {
        return sdf.format(new Date());
    }
}
