import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { ClientAppsComponent } from './components/client-apps/client-apps.component';
import { InstanceGroupAddEditComponent } from './components/instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './components/instance-group-browser/instance-group-browser.component';
import { ProductsComponent } from './components/products/products.component';

const IG_ROUTES: Route[] = [
  {
    path: 'browser',
    component: InstanceGroupBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instance Groups', header: 'Instance Groups' }
  },
  {
    path: 'add',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance Group', header: 'Add Instance Group' }
  },
  {
    path: 'edit/:name',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance Group (${params["name"]})', header: 'Edit Instance Group' }
  },
  {
    path: 'products/:group',
    component: ProductsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Manage Products (${params["group"]})', header: 'Manage Products' }
  },
  {
    path: 'clientapps/:group',
    component: ClientAppsComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Client Applications (${params["group"]})', header: 'Client Applications' }
  },
];

@NgModule({
  imports: [RouterModule.forChild(IG_ROUTES)],
  exports: [RouterModule],
})
export class InstanceGroupRoutingModule { }
