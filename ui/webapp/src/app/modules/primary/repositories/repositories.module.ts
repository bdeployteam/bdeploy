import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { RepositoriesBrowserComponent } from './components/repositories-browser/repositories-browser.component';
import { RepositoryComponent } from './components/repository/repository.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';

@NgModule({
  declarations: [RepositoriesBrowserComponent, RepositoryComponent],
  imports: [CommonModule, CoreModule, RepositoriesRoutingModule],
})
export class RepositoriesModule {}
