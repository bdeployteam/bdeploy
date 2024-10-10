import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ReportRequestDto } from 'src/app/models/gen.dtos';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { ReportInputChange } from '../form-input/report-form-input.component';

@Component({
  selector: 'app-report-form',
  templateUrl: './report-form.component.html',
})
export class ReportFormComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  protected readonly reports = inject(ReportsService);

  protected request: ReportRequestDto = { params: {} };
  protected changed$ = new BehaviorSubject<ReportInputChange>(null);

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.reports.current$.subscribe(() => (this.request = { params: {} }));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected generate() {
    this.reports
      .generateReport(this.request)
      .subscribe(() =>
        this.router.navigate([
          '',
          { outlets: { panel: ['panels', 'reports', this.reports.current$.value.type, 'view'] } },
        ]),
      );
  }
}
