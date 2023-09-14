import { Component, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { LoggingAdminService } from 'src/app/modules/primary/admin/services/logging-admin.service';

@Component({
  selector: 'app-log-config-editor',
  templateUrl: './log-config-editor.component.html',
})
export class LogConfigEditorComponent implements OnInit, DirtyableDialog {
  private loggingAdmin = inject(LoggingAdminService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected config: string;
  public origConfig: string;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  ngOnInit(): void {
    this.loggingAdmin
      .getLogConfig()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((config) => {
        this.config = Base64.decode(config);
        this.origConfig = this.config;
      });
  }

  public isDirty(): boolean {
    return this.config !== this.origConfig;
  }

  public doSave(): Observable<any> {
    return this.loggingAdmin.setLogConfig(Base64.encode(this.config));
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  @HostListener('window:keydown.control.s', ['$event'])
  public onCtrlS(event: KeyboardEvent) {
    this.onSave();
    event.preventDefault();
  }
}
