import { Component, Input, OnInit } from '@angular/core';
import { first, skipWhile } from 'rxjs/operators';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ConfigFile, ConfigFilesService } from '../../../../services/config-files.service';

export type ConfigFileStatusType = 'new' | 'modified' | 'local' | 'sync' | 'unsync' | 'missing';

@Component({
  selector: 'app-product-sync',
  templateUrl: './product-sync.component.html',
  styleUrls: ['./product-sync.component.css'],
})
export class ProductSyncComponent implements OnInit {
  @Input() record: ConfigFile;

  /* template */ path: string;
  /* template */ isText: boolean;
  /* template */ status: ConfigFileStatusType;

  constructor(private cfgFiles: ConfigFilesService, private edit: InstanceEditService) {}

  ngOnInit(): void {
    this.isText = this.cfgFiles.isText(this.record);
    this.path = this.cfgFiles.getPath(this.record);
    this.status = this.getStatus();
  }

  private getStatus(): ConfigFileStatusType {
    if (!this.record.persistent) {
      return 'new';
    }

    if (!this.record.persistent?.instanceId && !!this.record.persistent?.productId && !!this.record.modification?.file) {
      return 'new';
    }

    if (!!this.record.modification?.file) {
      return 'modified';
    }

    if (this.record.persistent.instanceId) {
      if (!this.record.persistent.productId) {
        return 'local';
      }

      if (this.record.persistent.instanceId.id === this.record.persistent.productId.id) {
        return 'sync';
      } else {
        return 'unsync';
      }
    } else {
      return 'missing';
    }
  }

  /* template */ doAddFromTemplate() {
    this.edit.state$
      .pipe(
        skipWhile((s) => !s?.config?.config?.product),
        first()
      )
      .subscribe((s) => {
        this.cfgFiles.loadTemplate(this.cfgFiles.getPath(this.record), s.config.config.product).subscribe((t) => {
          this.cfgFiles.add(this.cfgFiles.getPath(this.record), t, !this.record.persistent.isText);
        });
      });
  }

  /* template */ doRestoreFromTemplate() {
    this.edit.state$
      .pipe(
        skipWhile((s) => !s?.config?.config?.product),
        first()
      )
      .subscribe((s) => {
        this.cfgFiles.loadTemplate(this.cfgFiles.getPath(this.record), s.config.config.product).subscribe((t) => {
          this.cfgFiles.edit(this.cfgFiles.getPath(this.record), t);
        });
      });
  }
}
