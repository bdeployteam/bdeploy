import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, forkJoin, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ConfigFilesService } from '../../../../services/config-files.service';

@Component({
  selector: 'app-compare',
  templateUrl: './compare.component.html',
  styleUrls: ['./compare.component.css'],
})
export class CompareComponent implements OnInit, DirtyableDialog {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);
  /* template */ file$ = new BehaviorSubject<string>(null);
  /* template */ content = '';
  /* template */ originalContent = '';
  /* template */ contentTemplate = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(private bop: BreakpointObserver, public cfgFiles: ConfigFilesService, private edit: InstanceEditService, areas: NavAreasService) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      combineLatest([this.cfgFiles.files$, areas.panelRoute$, this.edit.state$]).subscribe(([f, r, s]) => {
        if (!f || !r || !r.params['file'] || !s?.config?.config?.product) {
          this.file$.next(null);
          this.content = null;
          return;
        }

        const file = r.params['file'];
        this.file$.next(file);
        forkJoin([this.cfgFiles.load(file), this.cfgFiles.loadTemplate(file, s.config.config.product)])
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe(([c, t]) => {
            this.content = Base64.decode(c);
            this.originalContent = this.content;
            this.contentTemplate = Base64.decode(t);
          });
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  doApply() {
    this.cfgFiles.edit(this.file$.value, Base64.encode(this.content));

    this.content = '';
    this.originalContent = '';
    this.tb.closePanel();
  }
}
