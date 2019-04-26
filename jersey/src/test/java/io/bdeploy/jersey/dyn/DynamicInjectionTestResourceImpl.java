package io.bdeploy.jersey.dyn;

import javax.inject.Inject;

public class DynamicInjectionTestResourceImpl implements DynamicTestResource {

    @Inject
    ValueDto value;

    @Override
    public ValueDto getValue() {
        return value;
    }

}
