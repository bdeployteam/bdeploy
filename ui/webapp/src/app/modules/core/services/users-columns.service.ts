import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDataUserAvatarCellComponent } from 'src/app/modules/core/components/bd-data-user-avatar-cell/bd-data-user-avatar-cell.component';

const colAvatar: BdDataColumn<UserInfo> = {
  id: 'avatar',
  name: 'Avatar',
  data: (r) => r.email,
  component: BdDataUserAvatarCellComponent,
  width: '30px',
};

const colName: BdDataColumn<UserInfo> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '250px',
};

const colFullName: BdDataColumn<UserInfo> = {
  id: 'fullName',
  name: 'Full Name',
  data: (r) => r.fullName,
  showWhen: '(min-width: 1300px)',
};

const colEmail: BdDataColumn<UserInfo> = {
  id: 'email',
  name: 'E-Mail',
  data: (r) => r.email,
  showWhen: '(min-width: 1400px)',
};

@Injectable({
  providedIn: 'root',
})
export class UsersColumnsService {
  public userGroupAdminDetailsColumns: BdDataColumn<UserInfo>[] = [colName, colFullName];
  public defaultUsersColumns: BdDataColumn<UserInfo>[] = [colAvatar, colName, colFullName, colEmail];
}
