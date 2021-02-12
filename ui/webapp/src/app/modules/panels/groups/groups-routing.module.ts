import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../../core/guards/authentication.guard';
import { AddGroupComponent } from './components/add-group/add-group.component';

const GROUPS_ROUTES: Route[] = [
  {
    path: 'add',
    component: AddGroupComponent,
    canActivate: [AuthGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
