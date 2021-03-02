import { Component, Input, OnInit } from '@angular/core';
import { ActivitySnapshot } from '../../../../../models/gen.dtos';
import { ActivitiesService, ActivitySnapshotTreeNode } from '../../../../core/services/activities.service';
import { AuthenticationService } from '../../../../core/services/authentication.service';

@Component({
  selector: 'app-remote-progress-element',
  templateUrl: './remote-progress-element.component.html',
  styleUrls: ['./remote-progress-element.component.css'],
})
export class RemoteProgressElementComponent implements OnInit {
  constructor(private events: ActivitiesService, private authService: AuthenticationService) {}

  @Input() element: ActivitySnapshotTreeNode;

  ngOnInit() {}

  getProgressValueFromElement(element: ActivitySnapshot): number {
    return (element.current * 100) / element.max;
  }

  cancel(element: ActivitySnapshot) {
    this.events.cancelActivity(element.uuid).subscribe((r) => {});
  }

  isCancelAllowed(element: ActivitySnapshot): boolean {
    return this.authService.getUsername() === element.user || this.authService.isGlobalAdmin();
  }
}
