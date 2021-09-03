import { Component, OnInit, ViewChild } from '@angular/core';
import { Subject } from 'rxjs';
import { BdTerminalComponent } from 'src/app/modules/core/components/bd-terminal/bd-terminal.component';
import { AuthAdminService } from '../../../services/auth-admin.service';

@Component({
  selector: 'app-auth-test',
  templateUrl: './auth-test.component.html',
  styleUrls: ['./auth-test.component.css'],
})
export class AuthTestComponent implements OnInit {
  /* template */ testUser = '';
  /* template */ testPass = '';

  /* template */ testResult$ = new Subject<string>();

  @ViewChild(BdTerminalComponent) terminal: BdTerminalComponent;

  constructor(private auth: AuthAdminService) {}

  ngOnInit(): void {}

  /* template */ performTest() {
    this.terminal.clear();
    this.testResult$.next('Checking...\n');
    this.auth.traceAuthentication(this.testUser, this.testPass).subscribe((r) => {
      this.testResult$.next(r.join('\n') + '\n');
    });
  }
}
