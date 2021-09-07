import { Component, OnInit } from '@angular/core';
import { AuthAdminService } from '../../services/auth-admin.service';

@Component({
  selector: 'app-settings-auth-ldap-server',
  templateUrl: './settings-auth-test-user.component.html',
  styleUrls: ['./settings-auth-test-user.component.css'],
})
export class SettingsAuthTestUserComponent implements OnInit {
  public loading = false;

  public model = { name: '', password: '' };
  public output: string = '';

  constructor(private authAdminService: AuthAdminService) {}

  ngOnInit() {}

  runTest() {
    this.output = '';
    this.authAdminService.traceAuthentication(this.model.name, this.model.password).subscribe((trace) => {
      trace.forEach((l) => (this.output += '\n' + l));
    });
  }
}
