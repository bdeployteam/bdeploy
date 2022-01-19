import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { UserInfo } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  selector: 'add-user',
  templateUrl: './add-user.component.html',
  styleUrls: ['./add-user.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddUserComponent implements OnInit {
  /* template */ addUser: Partial<UserInfo>;
  /* template */ addConfirm: string;

  private subscription: Subscription;

  constructor(private authAdmin: AuthAdminService, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.addUser = {};
    this.addConfirm = '';
  }

  /* template */ onSave() {
    this.subscription = this.authAdmin.createLocalUser(this.addUser as UserInfo).subscribe(() => {
      this.areas.closePanel();
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}
