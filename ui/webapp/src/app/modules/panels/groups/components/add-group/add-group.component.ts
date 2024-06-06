import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Component({
  selector: 'app-add-group',
  templateUrl: './add-group.component.html',
})
export class AddGroupComponent implements OnInit, OnDestroy, DirtyableDialog {
  private groups = inject(GroupsService);
  private repos = inject(RepositoriesService);
  private areas = inject(NavAreasService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected group: Partial<InstanceGroupConfiguration> = {
    autoDelete: true,
  };
  protected usedNames: string[] = [];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  private image: File;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');

    combineLatest([
      this.groups.groups$.pipe(map((g) => g.map((x) => x.instanceGroupConfiguration.name))),
      this.repos.repositories$.pipe(map((r) => r.map((y) => y.name))),
    ])
      .pipe(map(([g, r]) => [...g, ...r]))
      .subscribe((n) => {
        this.usedNames = n;
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onSelectImage(image: File) {
    this.image = image;
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onUnsupportedFile(file: File) {
    this.dialog.info('Unsupported File Type', `${file.name} has an unsupported file type.`, 'warning').subscribe();
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave().subscribe(() => {
      if (this.image) {
        this.groups.updateImage(this.group.name, this.image).subscribe(() => {
          this.reset();
        });
      } else {
        this.reset();
      }
    });
  }

  private reset() {
    this.areas.closePanel();
    this.subscription?.unsubscribe();
  }

  public doSave(): Observable<void> {
    this.saving$.next(true);
    return this.groups.create(this.group).pipe(
      finalize(() => {
        this.saving$.next(false);
      }),
    );
  }
}
