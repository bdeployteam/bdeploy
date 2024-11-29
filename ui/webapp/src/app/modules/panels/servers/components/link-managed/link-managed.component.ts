import { Component, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AttachType, ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ATTACH_MIME_TYPE } from '../../services/server-details.service';

@Component({
    selector: 'app-link-managed',
    templateUrl: './link-managed.component.html',
    styleUrls: ['./link-managed.component.css'],
    standalone: false
})
export class LinkManagedComponent {
  private readonly servers = inject(ServersService);
  private readonly areas = inject(NavAreasService);
  private readonly downloads = inject(DownloadService);
  private readonly snackbar = inject(MatSnackBar);

  protected payload: ManagedMasterDto;
  protected ident: string;
  protected manual = false;
  protected loadingIdent$ = new BehaviorSubject<boolean>(true);
  protected formSubmitted$ = new BehaviorSubject<boolean>(false);

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      this.setPayload(JSON.parse(reader.result.toString()));
    };
    reader.readAsText(file);
  }

  protected onSave() {
    this.formSubmitted$.next(true);
    this.servers.attachManaged(this.payload).subscribe({
      next: (type) => {
        if (type === AttachType.AUTO) {
          this.areas.closePanel();
        } else {
          this.manual = true;
          this.servers
            .getCentralIdent(this.payload)
            .pipe(finalize(() => this.loadingIdent$.next(false)))
            .subscribe((r) => (this.ident = r));
        }
      },
      error: () => this.formSubmitted$.next(false),
    });
  }

  protected onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.types.includes(ATTACH_MIME_TYPE)) {
      this.setPayload(JSON.parse(event.dataTransfer.getData(ATTACH_MIME_TYPE)));
    }
  }

  private setPayload(payload: ManagedMasterDto) {
    if (!payload?.auth?.length || !payload?.uri?.length || !payload?.hostName?.length) {
      this.snackbar.open('Invalid Data - Make sure to drag the correct card.', 'DISMISS', { duration: 10000 });
      return;
    }

    this.payload = payload;
  }

  protected onOver(event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if (event.preventDefault) {
      event.preventDefault();
    }

    return false;
  }

  protected fileAdded(event: any) {
    this.readFile(event);
  }

  protected onUpload(event: any) {
    if (event.target.files && event.target.files.length > 0) {
      this.readFile(event.target.files[0]);
    }
  }

  protected onDownloadCentralIdent() {
    this.downloads.downloadBlob(
      'central-' + this.payload.hostName + '.txt',
      new Blob([this.ident], { type: 'text/plain' }),
    );
    this.areas.closePanel();
  }
}
