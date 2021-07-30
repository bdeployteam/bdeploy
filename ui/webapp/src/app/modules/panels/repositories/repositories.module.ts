import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddRepositoryComponent } from './components/add-repository/add-repository.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { MaintenanceComponent } from './components/settings/maintenance/maintenance.component';
import { SettingsComponent } from './components/settings/settings.component';
import { LabelsComponent } from './components/software-details/labels/labels.component';
import { PluginsComponent } from './components/software-details/plugins/plugins.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { ApplicationComponent } from './components/software-details/templates/application/application.component';
import { InstanceComponent } from './components/software-details/templates/instance/instance.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';

@NgModule({
  declarations: [
    AddRepositoryComponent,
    SettingsComponent,
    EditComponent,
    MaintenanceComponent,
    SoftwareUploadComponent,
    SoftwareDetailsComponent,
    LabelsComponent,
    ApplicationComponent,
    InstanceComponent,
    PluginsComponent,
  ],
  imports: [CommonModule, CoreModule, RepositoriesRoutingModule],
})
export class RepositoriesModule {}
