import { ChangeDetectionStrategy, Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, debounceTime, filter, Subscription, switchMap, tap } from 'rxjs';
import {
  ReportDescriptor,
  ReportParameterDescriptor,
  ReportParameterInputType,
  ReportRequestDto,
} from 'src/app/models/gen.dtos';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { MatTooltip } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { BdFormToggleComponent } from '../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';

export interface ReportInputChange {
  key: string;
  value: string;
}

@Component({
  selector: 'app-report-form-input',
  templateUrl: './report-form-input.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdFormInputComponent, MatTooltip, FormsModule, BdFormToggleComponent, BdFormSelectComponent],
})
export class ReportFormInputComponent implements OnInit, OnDestroy {
  protected readonly reports = inject(ReportsService);
  protected readonly ReportParameterInputType = ReportParameterInputType;

  @Input() param: ReportParameterDescriptor;
  @Input() report: ReportDescriptor;
  @Input() request: ReportRequestDto;
  @Input() changed$: BehaviorSubject<ReportInputChange>;

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
        })
    );
    if (!this.param.dependsOn) {
      return;
    }
    this.subscriptions.push(
      this.changed$
        .pipe(
          filter((ch) => ch && this.param.dependsOn.includes(ch.key)),
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
            this.reports.getParameterOptions(this.param.parameterOptionsPath, this.param.dependsOn, this.request.params)
          )
        )
        .subscribe((ps) => {
          this.values = ps.map((p) => p.value);
          this.labels = ps.map((p) => p.label);
        })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
  }

  protected onModelChange(event: string) {
    this.changed$.next({ key: this.param.key, value: event });
  }
}
