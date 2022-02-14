import { Component } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  AttachType,
  ServersService,
} from 'src/app/modules/primary/servers/services/servers.service';
import { ATTACH_MIME_TYPE } from '../../services/server-details.service';

@Component({
  selector: 'app-link-managed',
  templateUrl: './link-managed.component.html',
  styleUrls: ['./link-managed.component.css'],
})
export class LinkManagedComponent {
  /* template */ payload: ManagedMasterDto;
  /* template */ ident: string;
  /* template */ manual = false;
  /* template */ loadingIdent$ = new BehaviorSubject<boolean>(true);

  constructor(
    private servers: ServersService,
    private areas: NavAreasService,
    private downloads: DownloadService
  ) {}

  private readFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      this.payload = JSON.parse(reader.result.toString());
    };
    reader.readAsText(file);
  }

  /* template */ onSave() {
    this.servers.attachManaged(this.payload).subscribe((type) => {
      if (type === AttachType.AUTO) {
        this.areas.closePanel();
      } else {
        this.manual = true;
        this.servers
          .getCentralIdent(this.payload)
          .pipe(finalize(() => this.loadingIdent$.next(false)))
          .subscribe((r) => (this.ident = r));
      }
    });
  }

  /* template */ onDrop(event: DragEvent) {
    event.preventDefault();

    if (event.dataTransfer.files.length > 0) {
      this.readFile(event.dataTransfer.files[0]);
    } else if (event.dataTransfer.types.includes(ATTACH_MIME_TYPE)) {
      this.payload = JSON.parse(event.dataTransfer.getData(ATTACH_MIME_TYPE));
    }
  }

  /* template */ onOver(event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if (event.preventDefault) {
      event.preventDefault();
    }

    return false;
  }

  /* template */ onUpload(event: any) {
    if (event.target.files && event.target.files.length > 0) {
      this.readFile(event.target.files[0]);
    }
  }

  /* template */ onDownloadCentralIdent() {
    this.downloads.downloadBlob(
      'central-' + this.payload.hostName + '.txt',
      new Blob([this.ident], { type: 'text/plain' })
    );
    this.areas.closePanel();
  }
}
