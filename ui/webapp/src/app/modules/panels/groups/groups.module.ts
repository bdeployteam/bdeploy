import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { GroupsRoutingModule } from './groups-routing.module';

@NgModule({
  declarations: [AddGroupComponent],
  imports: [CommonModule, GroupsRoutingModule],
})
export class GroupsModule {}
