import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../../services/server-details.service';

@Component({
  selector: 'app-server-edit',
  templateUrl: './server-edit.component.html',
  styleUrls: ['./server-edit.component.css'],
  providers: [ServerDetailsService],
})
export class ServerEditComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* tepmlate */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ loading$ = combineLatest([this.saving$, this.servers.loading$, this.details.loading$]).pipe(map(([a, b, c]) => a || b || c));

  /* template */ server: ManagedMasterDto;
  /* template */ orig: ManagedMasterDto;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  private subscription: Subscription;

  constructor(private servers: ServersService, public details: ServerDetailsService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      this.details.server$.subscribe((s) => {
        this.server = s;
        this.orig = cloneDeep(s);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isDirty() {
    return isDirty(this.server, this.orig);
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.details
      .update(this.server)
      .pipe(finalize(() => this.saving$.next(false)))
      .subscribe((_) => {
        this.tb.closePanel();
      });
  }
}
