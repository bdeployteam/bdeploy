package io.bdeploy.jersey.dyn;

public class DynamicTestResourceImpl implements DynamicTestResource {

    private final String value;

    public DynamicTestResourceImpl(String value) {
        this.value = value;
    }

    @Override
    public ValueDto getValue() {
        return new ValueDto(value);
    }

}
