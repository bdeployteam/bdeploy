package io.bdeploy.interfaces.report;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReportResponseDto {

    public List<Map<String, String>> rows = new ArrayList<>();

    public Date generatedAt;

}
