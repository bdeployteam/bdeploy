import { Component, ViewChild, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { BdTerminalComponent } from 'src/app/modules/core/components/bd-terminal/bd-terminal.component';
import { AuthAdminService } from '../../../../primary/admin/services/auth-admin.service';

@Component({
  selector: 'app-auth-test',
  templateUrl: './auth-test.component.html',
  styleUrls: ['./auth-test.component.css'],
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
