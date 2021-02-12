import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ProductService } from '../../legacy/shared/services/product.service';
import { SharedModule } from '../../legacy/shared/shared.module';
import { CoreLegacyModule } from '../core/core-legacy.module';
import { SoftwareCardComponent } from './components/software-card/software-card.component';
import { SoftwareListComponent } from './components/software-list/software-list.component';
import { SoftwareRepoAddEditComponent } from './components/software-repo-add-edit/software-repo-add-edit.component';
import { SoftwareRepoFileUploadComponent } from './components/software-repo-file-upload/software-repo-file-upload.component';
import { SoftwareRepositoriesBrowserComponent } from './components/software-repositories-browser/software-repositories-browser.component';
import { SoftwareRepositoryCardComponent } from './components/software-repository-card/software-repository-card.component';
import { SoftwareRepositoryPermissionsComponent } from './components/software-repository-permissions/software-repository-permissions.component';
import { SoftwareRepositoryComponent } from './components/software-repository/software-repository.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';
import { SoftwareRepositoryService } from './services/software-repository.service';

@NgModule({
  declarations: [
    SoftwareRepositoriesBrowserComponent,
    SoftwareRepositoryComponent,
    SoftwareRepositoryCardComponent,
    SoftwareRepositoryPermissionsComponent,
    SoftwareRepoAddEditComponent,
    SoftwareCardComponent,
    SoftwareListComponent,
    SoftwareRepoFileUploadComponent,
  ],
  providers: [ProductService, { provide: 'ProductBasePath', useValue: SoftwareRepositoryService.BASEPATH }],
  imports: [CommonModule, SharedModule, CoreModule, CoreLegacyModule, RepositoriesRoutingModule],
})
export class RepositoriesModule {}
