package io.bdeploy.interfaces.manifest.statistics;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ClientUsageData {

    @JsonIgnore
    private final static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    // day -> applicationId -> hostname -> count
    private final SortedMap<String, SortedMap<String, SortedMap<String, Integer>>> clientUsage = new TreeMap<>();

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
        return LocalDateTime.now().format(dtf);
    }
}
