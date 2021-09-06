import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';

const routes: Routes = [
  { path: 'add-plugin', component: AddPluginComponent, canActivate: [AdminGuard] },
  { path: 'user-detail/:user', component: UserAdminDetailComponent, canActivate: [AdminGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
