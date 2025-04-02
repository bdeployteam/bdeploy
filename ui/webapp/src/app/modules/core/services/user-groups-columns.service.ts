import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { UserGroupInfo } from 'src/app/models/gen.dtos';

const colName: BdDataColumn<UserGroupInfo, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '250px',
};

const colDescription: BdDataColumn<UserGroupInfo, string> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
};

@Injectable({
  providedIn: 'root',
})
export class UserGroupsColumnsService {
  public readonly defaultColumns: BdDataColumn<UserGroupInfo, unknown>[] = [colName, colDescription];
}
