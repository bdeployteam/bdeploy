package io.bdeploy.ui.dto;

public class ReportParameterOptionDto {

    public final String label;

    public final String value;

    public ReportParameterOptionDto(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public ReportParameterOptionDto(String value) {
        this(value, value);
    }

}
