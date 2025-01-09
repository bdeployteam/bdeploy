import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, finalize, Subscription } from 'rxjs';
import { ReportRequestDto } from 'src/app/models/gen.dtos';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { ReportInputChange, ReportFormInputComponent } from '../form-input/report-form-input.component';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { FormsModule } from '@angular/forms';
import { MatTooltip } from '@angular/material/tooltip';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-report-form',
    templateUrl: './report-form.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, ReportFormInputComponent, MatTooltip, BdButtonComponent, AsyncPipe]
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
