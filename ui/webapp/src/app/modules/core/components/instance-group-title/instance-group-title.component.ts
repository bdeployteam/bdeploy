import { Component, Input, OnInit } from '@angular/core';
import { InstanceGroupService } from 'src/app/modules/instance-group/services/instance-group.service';

@Component({
  selector: 'app-instance-group-title',
  templateUrl: './instance-group-title.component.html',
  styleUrls: ['./instance-group-title.component.css']
})
export class InstanceGroupTitleComponent implements OnInit {

  @Input()
  instanceGroup: string;

  title: string;

  constructor(private igService: InstanceGroupService) { }

  ngOnInit(): void {
    this.title = this.instanceGroup;
    this.igService.getInstanceGroup(this.instanceGroup).subscribe(r => {
      this.title = r.title;
    });
  }

}
