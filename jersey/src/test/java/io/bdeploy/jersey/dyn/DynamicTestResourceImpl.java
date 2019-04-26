package io.bdeploy.jersey.dyn;

public class DynamicTestResourceImpl implements DynamicTestResource {

    private String value;

    public DynamicTestResourceImpl(String value) {
        this.value = value;
    }

    public ValueDto getValue() {
        return new ValueDto(value);
    }

}
