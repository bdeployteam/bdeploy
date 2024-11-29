import { HttpClient } from '@angular/common/http';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
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
    standalone: false
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly areas = inject(NavAreasService);
  protected readonly groups = inject(GroupsService);
  protected readonly details = inject(GroupDetailsService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected group: InstanceGroupConfiguration;
  protected origGroup: InstanceGroupConfiguration;
  protected origImage$ = new Subject<SafeUrl>();
  protected disableSave: boolean;
  private image: File;
  private imageChanged = false;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      this.groups.current$.subscribe((g) => {
        if (!g) {
          this.group = null;
          return;
        }

        this.group = cloneDeep(g);
        this.origGroup = cloneDeep(g);

        if (g.logo) {
          const url = this.groups.getLogoUrlOrDefault(g.name, g.logo, null);
          this.http.get(url, { responseType: 'blob' }).subscribe((data) => {
            const reader = new FileReader();
            reader.onload = () => {
              this.origImage$.next(this.sanitizer.bypassSecurityTrustUrl(reader.result.toString()));
            };
            reader.readAsDataURL(data);
          });
        }
      }),
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.disableSave = this.isDirty();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.group, this.origGroup) || this.imageChanged;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSelectImage(image: File) {
    this.imageChanged = true;
    this.image = image;
    this.disableSave = this.isDirty();
  }

  protected onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning').subscribe();
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(finalize(() => this.saving$.next(false)))
      .subscribe(() => this.reset());
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);
    if (this.imageChanged) {
      return this.details
        .update(this.group)
        .pipe(
          concatMap(() =>
            this.image
              ? this.groups.updateImage(this.group.name, this.image)
              : this.groups.removeImage(this.group.name),
          ),
        );
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
