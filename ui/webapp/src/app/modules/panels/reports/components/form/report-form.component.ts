import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, finalize, Subscription } from 'rxjs';
import { ReportRequestDto } from 'src/app/models/gen.dtos';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { ReportInputChange } from '../form-input/report-form-input.component';

@Component({
    selector: 'app-report-form',
    templateUrl: './report-form.component.html',
    standalone: false
})
export class ReportFormComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  protected readonly reports = inject(ReportsService);

  protected request: ReportRequestDto = { params: {} };
  protected changed$ = new BehaviorSubject<ReportInputChange>(null);
  protected loading$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.reports.current$.subscribe(() => (this.request = { params: {} }));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected generate() {
    this.loading$.next(true);
    this.reports
      .generateReport(this.request)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(() => this.router.navigate(['reports', 'browser', this.reports.current$.value.type, 'view']));
  }
}
