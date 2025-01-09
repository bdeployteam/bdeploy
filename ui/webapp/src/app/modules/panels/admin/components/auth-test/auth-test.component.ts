import { Component, inject, ViewChild } from '@angular/core';
import { Subject } from 'rxjs';
import { BdTerminalComponent } from 'src/app/modules/core/components/bd-terminal/bd-terminal.component';
import { AuthAdminService } from '../../../../primary/admin/services/auth-admin.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';


@Component({
    selector: 'app-auth-test',
    templateUrl: './auth-test.component.html',
    styleUrls: ['./auth-test.component.css'],
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFormInputComponent, FormsModule, BdButtonComponent, BdTerminalComponent]
})
export class AuthTestComponent {
  private readonly auth = inject(AuthAdminService);

  protected testUser = '';
  protected testPass = '';

  protected testResult$ = new Subject<string>();

  @ViewChild(BdTerminalComponent) terminal: BdTerminalComponent;

  protected performTest() {
    this.terminal.clear();
    this.testResult$.next('Checking...\n');
    this.auth.traceAuthentication(this.testUser, this.testPass).subscribe((r) => {
      this.testResult$.next(r.join('\n') + '\n');
    });
  }
}
