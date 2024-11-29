import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { Actions, ManagedMasterDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../services/server-details.service';

@Component({
    selector: 'app-server-details',
    templateUrl: './server-details.component.html',
    providers: [ServerDetailsService],
    standalone: false
})
export class ServerDetailsComponent implements OnInit {
  private readonly actions = inject(ActionsService);
  protected readonly servers = inject(ServersService);
  protected readonly serverDetails = inject(ServerDetailsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly areas = inject(NavAreasService);

  private readonly deleting$ = new BehaviorSubject<boolean>(false);

  private readonly transfering$ = new BehaviorSubject<boolean>(false);
  private readonly installing$ = new BehaviorSubject<boolean>(false);

  private readonly s$ = this.serverDetails.server$.pipe(map((s) => (!s ? '__DUMMY__' : s.hostName)));

  protected mappedTransfer$ = this.actions.action(
    [Actions.MANAGED_UPDATE_TRANSFER],
    this.transfering$,
    null,
    null,
    this.s$,
  );
  protected mappedInstall$ = this.actions.action(
    [Actions.MANAGED_UPDATE_INSTALL],
    this.installing$,
    null,
    null,
    this.s$,
  );
  private readonly mappedDelete$ = this.actions.action([Actions.REMOVE_MANAGED], this.deleting$, null, null, this.s$);

  protected loading$ = combineLatest([this.mappedDelete$, this.servers.loading$, this.serverDetails.loading$]).pipe(
    map(([a, b, c]) => a || b || c),
  );

  protected version: string;
  protected server: ManagedMasterDto;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit(): void {
    this.serverDetails.server$.subscribe((server) => {
      if (!server) {
        return;
      }
      this.server = server;
      this.version = convert2String(server.update?.updateVersion);
    });
  }

  protected doDelete(server: ManagedMasterDto) {
    this.dialog
      .confirm(
        `Delete ${server.hostName}`,
        `Are you sure you want to delete ${server.hostName}?` +
          (!this.serverDetails.instances$.value?.length
            ? ''
            : ` This will delete <strong>${this.serverDetails.instances$.value.length} instance(s)</strong> from the central server ` +
              '- they will <strong>not</strong> be deleted from the managed server.'),
      )
      .subscribe((del) => {
        if (del) {
          this.deleting$.next(true);
          this.serverDetails
            .delete(server)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => this.areas.closePanel());
        }
      });
  }

  protected doUpdateTransfer(server: ManagedMasterDto) {
    this.transfering$.next(true);
    this.serverDetails
      .remoteUpdateTransfer(server)
      .pipe(finalize(() => this.transfering$.next(false)))
      .subscribe();
  }

  protected doUpdateInstall(server: ManagedMasterDto) {
    this.installing$.next(true);
    this.serverDetails
      .remoteUpdateInstall(server)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe({
        next: () => {
          this.dialog
            .info(
              'Update complete',
              `The server has come back online after updating, the current server version is ${this.version}`,
            )
            .subscribe();
        },
        error: (err) => {
          let msg = err;
          if (err instanceof Error) {
            msg = err.message;
          } else if (err instanceof HttpErrorResponse) {
            msg = err.statusText;
          }
          this.dialog.info('Eror Updating', `There was an error applying the update to the server: ${msg}`).subscribe();
        },
      });
  }
}
