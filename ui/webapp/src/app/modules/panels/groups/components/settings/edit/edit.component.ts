import { HttpClient } from '@angular/common/http';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { concatMap, debounceTime, finalize } from 'rxjs/operators';
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
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ group: InstanceGroupConfiguration;
  /* template */ origGroup: InstanceGroupConfiguration;
  /* template */ origImage$ = new Subject<SafeUrl>();
  /* template */ disableSave: boolean;
  private image: File;
  private imageChanged = false;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

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
    this.subscription.add(
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
      })
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.disableSave = this.isDirty();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.group, this.origGroup) || this.imageChanged;
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSelectImage(image: File) {
    this.imageChanged = true;
    this.image = image;
    this.disableSave = this.isDirty();
  }

  /* template */ onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning').subscribe();
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(finalize(() => this.saving$.next(false)))
      .subscribe((_) => this.reset());
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    if (this.imageChanged) {
      return this.details
        .update(this.group)
        .pipe(concatMap((_) => (this.image ? this.groups.updateImage(this.group.name, this.image) : this.groups.removeImage(this.group.name))));
    }
    return this.details.update(this.group);
  }

  private reset() {
    this.imageChanged = false;
    this.saving$.next(false);
    this.group = this.origGroup;
    this.tb.closePanel();
  }
}
