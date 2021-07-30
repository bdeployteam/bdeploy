import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { RepositoriesBrowserComponent } from './components/repositories-browser/repositories-browser.component';
import { RepositoryComponent } from './components/repository/repository.component';

const GROUPS_ROUTES: Route[] = [
  { path: 'browser', component: RepositoriesBrowserComponent, canDeactivate: [DirtyDialogGuard] },
  { path: 'repository/:repository', component: RepositoryComponent, canDeactivate: [DirtyDialogGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class RepositoriesRoutingModule {}
