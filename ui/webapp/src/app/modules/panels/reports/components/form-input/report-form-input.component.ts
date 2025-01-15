import { ChangeDetectionStrategy, Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, debounceTime, filter, Subscription, switchMap, tap } from 'rxjs';
import {
  ReportDescriptor,
  ReportParameterDescriptor,
  ReportParameterInputType,
  ReportRequestDto,
} from 'src/app/models/gen.dtos';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

export interface ReportInputChange {
  key: string;
  value: string;
}

@Component({
    selector: 'app-report-form-input',
    templateUrl: './report-form-input.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class ReportFormInputComponent implements OnInit, OnDestroy {
  protected readonly reports = inject(ReportsService);
  protected readonly ReportParameterInputType = ReportParameterInputType;

  @Input()
  protected param: ReportParameterDescriptor;
  @Input()
  protected report: ReportDescriptor;
  @Input()
  protected request: ReportRequestDto;
  @Input()
  changed$: BehaviorSubject<ReportInputChange>;

  protected values: string[] = [];
  protected labels: string[] = [];

  private readonly subscriptions: Subscription[] = [];

  ngOnInit(): void {
    if (!this.param.parameterOptionsPath) {
      return;
    }
    this.subscriptions.push(
      this.reports
        .getParameterOptions(this.param.parameterOptionsPath, this.param.dependsOn, this.request.params)
        .subscribe((ps) => {
          this.values = ps.map((p) => p.value);
          this.labels = ps.map((p) => p.label);
        }),
    );
    if (!this.param.dependsOn) {
      return;
    }
    this.subscriptions.push(
      this.changed$
        .pipe(
          filter((ch) => ch && this.param.dependsOn.indexOf(ch.key) !== -1),
          tap(() => {
            this.values = [];
            this.labels = [];
            if (this.param.inputType === ReportParameterInputType.SELECT) {
              this.request.params[this.param.key] = null;
              this.onModelChange(null);
            }
          }),
          debounceTime(100),
          switchMap(() =>
            this.reports.getParameterOptions(
              this.param.parameterOptionsPath,
              this.param.dependsOn,
              this.request.params,
            ),
          ),
        )
        .subscribe((ps) => {
          this.values = ps.map((p) => p.value);
          this.labels = ps.map((p) => p.label);
        }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
  }

  protected onModelChange(event: string) {
    this.changed$.next({ key: this.param.key, value: event });
  }
}
