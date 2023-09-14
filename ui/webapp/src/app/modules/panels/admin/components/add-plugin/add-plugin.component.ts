import { Component, inject } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { UploadStatus } from 'src/app/modules/core/services/upload.service';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';

@Component({
  selector: 'app-add-plugin',
  templateUrl: './add-plugin.component.html',
})
export class AddPluginComponent {
  protected plugins = inject(PluginAdminService);

  protected files: File[] = [];

  protected resultEvaluator(result: UploadStatus): string {
    if (!result.detail) {
      return null;
    }

    const details = result.detail as PluginInfoDto;
    return 'Added ' + details.name + ' ' + details.version;
  }

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
