import { Component, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuditLogDto } from 'src/app/models/gen.dtos';
import { BdDialogScrollEvent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuditService } from 'src/app/modules/primary/admin/services/audit.service';
import { AuditColumnsService } from '../../../services/audit-columns.service';

@Component({
  selector: 'app-bhive-audit',
  templateUrl: './bhive-audit.component.html',
  styleUrls: ['./bhive-audit.component.css'],
})
export class BhiveAuditComponent implements OnInit {
  /* template */ bhive$ = new BehaviorSubject<string>(null);
  /* template */ logs$ = new BehaviorSubject<AuditLogDto[]>([]);

  private subscription: Subscription;

  constructor(areas: NavAreasService, public audit: AuditService, public columns: AuditColumnsService) {
    this.subscription = areas.panelRoute$.subscribe((route) => {
      if (!route?.params || !route?.params['bhive']) {
        return;
      }

      this.bhive$.next(route.params['bhive']);
      this.audit.hiveAuditLog(route.params['bhive'], 0, 100).subscribe((logs) => {
        this.logs$.next(logs);
      });
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onScroll(event: BdDialogScrollEvent) {
    if (!this.logs$.value?.length || event !== BdDialogScrollEvent.NEAR_BOTTOM || this.audit.loading$.value) {
      return;
    }

    const lastInstant = this.logs$.value[this.logs$.value.length - 1].instant;
    this.audit.hiveAuditLog(this.bhive$.value, lastInstant, 100).subscribe((logs) => {
      this.logs$.next(this.logs$.value.concat(logs));
    });
  }
}
