package io.bdeploy.interfaces.manifest.statistics;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.SortedMap;
import java.util.TreeMap;

public class ClientUsageData {

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    // day -> applicationId -> hostname -> count
    SortedMap<String, SortedMap<String, SortedMap<String, Integer>>> clientUsage = new TreeMap<>();

    public void increment(String applicationId, String hostname) {
        String today = getToday();

        SortedMap<String, SortedMap<String, Integer>> applicationMap = clientUsage.computeIfAbsent(today, t -> new TreeMap<>());
        SortedMap<String, Integer> hostnameMap = applicationMap.computeIfAbsent(applicationId, a -> new TreeMap<>());
        hostnameMap.compute(hostname, (k, count) -> count == null ? 1 : ++count);
    }

    private String getToday() {
        return sdf.format(Calendar.getInstance().getTime()); // TODO maybe use UTC here?
    }
}
