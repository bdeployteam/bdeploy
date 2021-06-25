import { Component, OnInit } from '@angular/core';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Component({
  selector: 'app-permissions',
  templateUrl: './permissions.component.html',
  styleUrls: ['./permissions.component.css'],
})
export class PermissionsComponent implements OnInit {
  constructor(public groups: GroupsService) {}

  ngOnInit(): void {}
}
