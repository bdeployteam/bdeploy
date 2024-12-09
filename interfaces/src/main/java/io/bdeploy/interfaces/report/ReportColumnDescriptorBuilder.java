package io.bdeploy.interfaces.report;

/**
 * Builder class for {@link ReportColumnDescriptor}.
 */
class ReportColumnDescriptorBuilder {

    private final String name;

    private final String key;

    private boolean main = false;

    private boolean identifier = false;

    ReportColumnDescriptorBuilder(String name, String key) {
        this.name = name;
        this.key = key;
    }

    ReportColumnDescriptorBuilder main() {
        this.main = true;
        return this;
    }

    ReportColumnDescriptorBuilder identifier() {
        this.identifier = true;
        return this;
    }

    ReportColumnDescriptor build() {
        return new ReportColumnDescriptor(name, key, main, identifier);
    }
}
