import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { AddGroupComponent } from './components/add-group/add-group.component';

const GROUPS_ROUTES: Route[] = [{ path: 'add', component: AddGroupComponent, canActivate: [AdminGuard] }];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
