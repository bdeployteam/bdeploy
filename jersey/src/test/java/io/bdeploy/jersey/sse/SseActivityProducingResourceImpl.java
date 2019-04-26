package io.bdeploy.jersey.sse;

import javax.inject.Inject;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;

public class SseActivityProducingResourceImpl implements SseActivityProducingResource {

    @Inject
    private ActivityReporter reporter;

    @Override
    public String something(String test) {
        Activity start = reporter.start("Test", 5);
        for (int i = 0; i < 6; ++i) {
            start.worked(1);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return "wohoo";
    }

}
