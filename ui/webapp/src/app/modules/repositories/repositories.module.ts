import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { InstanceGroupModule } from '../instance-group/instance-group.module';
import { SharedModule } from '../shared/shared.module';
import { SoftwareCardComponent } from './components/software-card/software-card.component';
import { SoftwareListComponent } from './components/software-list/software-list.component';
import { SoftwareRepoAddEditComponent } from './components/software-repo-add-edit/software-repo-add-edit.component';
import { SoftwareRepositoriesBrowserComponent } from './components/software-repositories-browser/software-repositories-browser.component';
import { SoftwareRepositoryCardComponent } from './components/software-repository-card/software-repository-card.component';
import { SoftwareRepositoryComponent } from './components/software-repository/software-repository.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';



@NgModule({
  declarations: [
    SoftwareRepositoriesBrowserComponent,
    SoftwareRepositoryComponent,
    SoftwareRepositoryCardComponent,
    SoftwareRepoAddEditComponent,
    SoftwareCardComponent,
    SoftwareListComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    RepositoriesRoutingModule,
    InstanceGroupModule
  ]
})
export class RepositoriesModule { }
