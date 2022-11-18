package io.bdeploy.common.cli.data;

public enum ExitCode {

    OK(0),
    ERROR(1);

    private int code;

    private ExitCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
