import { Component, OnDestroy, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { CleanupGroup } from '../../../../models/gen.dtos';
import { CleanupService } from '../../services/cleanup.service';

@Component({
  selector: 'app-master-cleanup',
  templateUrl: './master-cleanup.component.html',
  styleUrls: ['./master-cleanup.component.css'],
})
export class MasterCleanupComponent implements OnInit, OnDestroy {
  loadingCleanupModel = false;
  cleanupModel: CleanupGroup[];
  clearCounter = 0;
  clearCountDownHandle: any;

  performingCleanupModel = false;

  constructor(private cleanupService: CleanupService) {}

  ngOnInit() {}

  ngOnDestroy() {
    if (this.clearCountDownHandle) {
      clearInterval(this.clearCountDownHandle);
    }
  }

  onCalculate() {
    this.loadingCleanupModel = true;
    this.cleanupModel = null;
    this.cleanupService
      .calculateCleanup()
      .pipe(finalize(() => (this.loadingCleanupModel = false)))
      .subscribe((r) => {
        this.cleanupModel = r.filter((g) => g.actions.length > 0);
        this.clearCounter = 600;
        this.clearCountDownHandle = setInterval(() => this.clearCountDown(), 1000);
      });
  }

  public getNonemptyGroups(): CleanupGroup[] {
    return this.cleanupModel;
  }

  clearCountDown() {
    if (this.clearCounter <= 0 || !this.cleanupModel) {
      if (this.clearCountDownHandle) {
        clearInterval(this.clearCountDownHandle);
      }
      return;
    }

    this.clearCounter--;
    if (this.clearCounter === 0) {
      this.onClear();
    }
  }

  onClear() {
    this.cleanupModel = null;
  }

  onPerform() {
    this.performingCleanupModel = true;
    this.cleanupService
      .performCleanup(this.cleanupModel)
      .pipe(
        finalize(() => {
          this.performingCleanupModel = false;
          this.cleanupModel = null;
        })
      )
      .subscribe((r) => {});
  }

  hasAction(): boolean {
    for (const g of this.cleanupModel) {
      if (g.actions && g.actions.length) {
        return true;
      }
    }
    return false;
  }
}
