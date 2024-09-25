package io.bdeploy.ui.report;

import io.bdeploy.interfaces.report.ReportRequestDto;
import io.bdeploy.interfaces.report.ReportResponseDto;

public interface ReportGenerator {

    ReportResponseDto generateReport(ReportRequestDto request);
}
