package io.bdeploy.jersey.dyn;

import javax.inject.Inject;

public class DynamicInjectionTestResourceLocatorImpl implements DynamicInjectionTestResourceLocator {

    @Inject
    private DynamicTestResource rsrc;

    @Override
    public DynamicTestResource getNamed(String name) {
        return rsrc;
    }

}
