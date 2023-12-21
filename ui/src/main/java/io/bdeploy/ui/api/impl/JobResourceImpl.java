package io.bdeploy.ui.api.impl;

import java.util.List;

import io.bdeploy.ui.api.JobResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.JobDto;
import jakarta.inject.Inject;

public class JobResourceImpl implements JobResource {

    @Inject
    private Minion minion;

    @Override
    public List<JobDto> list() {
        return minion.listJobs();
    }

    @Override
    public void run(JobDto jobDto) {
        minion.runJob(jobDto);
    }

}
