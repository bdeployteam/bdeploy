package io.bdeploy.ui.api.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.ui.api.AuditResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.AuditLogDto;

public class AuditResourceImpl implements AuditResource {

    private static final Logger log = LoggerFactory.getLogger(AuditResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Context
    private SecurityContext context;

    @Override
    public List<AuditLogDto> hiveAuditLog(String hiveParam, long lastInstant, int lineLimit) {
        log.debug("hiveAuditLog(\"{}\",\"{}\",\\\"{}\\\")", hiveParam, lastInstant, lineLimit);
        BHive hive = registry.get(hiveParam);
        if (hive != null && hive.getAuditor() instanceof RollingFileAuditor) {
            return scanFiles((RollingFileAuditor) hive.getAuditor(), lastInstant, lineLimit);
        } else {
            throw new WebApplicationException("Cannot read auditor of hive " + hiveParam);
        }
    }

    @Override
    public List<AuditLogDto> auditLog(long lastInstant, int lineLimit) {
        log.debug("auditLog(\"{}\",\\\"{}\\\")", lastInstant, lineLimit);
        return scanFiles((RollingFileAuditor) minion.getAuditor(), lastInstant, lineLimit);
    }

    private List<AuditLogDto> scanFiles(RollingFileAuditor auditor, long lastInstant, int lineLimit) {
        log.debug("scanFiles(<auditor>,\"{}\",\\\"{}\\\")", lastInstant, lineLimit);
        List<AuditLogDto> result = new ArrayList<>();
        Path currentPath = auditor.getJsonFile();
        InputStream is = null;
        try {
            // scan current log
            is = Files.newInputStream(currentPath);
            boolean readMore = scanFile(result, is, lastInstant, lineLimit);
            is.close();
            is = null;

            // scan archived logs
            Path[] backupFiles = auditor.getJsonBackups();
            for (int i = 0; readMore && i < backupFiles.length; i++) {
                currentPath = backupFiles[i];
                is = new GZIPInputStream(Files.newInputStream(currentPath));
                readMore = scanFile(result, is, lastInstant, lineLimit);
                is.close();
                is = null;
            }
        } catch (IOException e) {
            log.error("Failed to open " + currentPath.toString(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ee) {
                    log.error("Failed to close " + currentPath.toString(), ee);
                }
            }
        }
        return result;
    }

    private boolean scanFile(List<AuditLogDto> result, InputStream stream, long lastInstant, int limit) throws IOException {
        ObjectMapper mapper = JacksonHelper.createDefaultObjectMapper();
        List<AuditLogDto> chunk = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));

        String line;
        boolean first = true;
        while ((line = br.readLine()) != null) {
            AuditLogDto dto = mapper.readValue(line, AuditLogDto.class);

            if (first) {
                first = false;
                // check if the whole file is out of range (limited to records that are older than the first one)
                // if yes, stop reading here
                if (lastInstant > 0 && lastInstant < dto.instant.toEpochMilli()) {
                    return true;
                }
            }

            if ((lastInstant == 0 || lastInstant > dto.instant.toEpochMilli())) {
                chunk.add(dto);
            }
        }
        int required = Math.max(0, limit - result.size());
        if (required >= chunk.size()) {
            result.addAll(0, chunk);
        } else {
            // take all records with the same instant value (and exceed the limit)
            int idx = chunk.size() - required;
            while (idx > 0 && chunk.get(idx).instant.toEpochMilli() == chunk.get(idx - 1).instant.toEpochMilli()) {
                idx--;
            }
            result.addAll(0, chunk.subList(idx, chunk.size()));
        }
        return result.size() < limit;
    }

}
