import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupDetailsService } from '../../../services/group-details.service';

@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
  styleUrls: ['./edit.component.css'],
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ group: InstanceGroupConfiguration;
  /* template */ origGroup: InstanceGroupConfiguration;
  /* template */ origImage$ = new Subject<SafeUrl>();
  private image: File;
  private imageChanged = false;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(
    public groups: GroupsService,
    public details: GroupDetailsService,
    private http: HttpClient,
    private sanitizer: DomSanitizer,
    areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.groups.current$.subscribe((g) => {
      if (!g) {
        this.group = null;
        return;
      }

      this.group = cloneDeep(g);
      this.origGroup = cloneDeep(g);

      if (!!g.logo) {
        const url = this.groups.getLogoUrlOrDefault(g.name, g.logo, null);
        this.http.get(url, { responseType: 'blob' }).subscribe((data) => {
          const reader = new FileReader();
          reader.onload = () => {
            this.origImage$.next(this.sanitizer.bypassSecurityTrustUrl(reader.result.toString()));
          };
          reader.readAsDataURL(data);
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isDirty(): boolean {
    return isDirty(this.group, this.origGroup) || this.imageChanged;
  }

  /* template */ onSelectImage(image: File) {
    this.imageChanged = true;
    this.image = image;
  }

  /* template */ onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning');
  }

  /* template */ onSave(): void {
    this.saving$.next(true);
    this.details.update(this.group).subscribe(
      (_) => {
        if (this.imageChanged) {
          this.groups
            .updateImage(this.group.name, this.image)
            .pipe(finalize(() => this.reset()))
            .subscribe();
        } else {
          this.reset();
        }
      },
      (err) => this.saving$.next(false)
    );
  }

  private reset() {
    this.imageChanged = false;
    this.saving$.next(false);
    this.group = this.origGroup;
    this.tb.closePanel();
  }
}
