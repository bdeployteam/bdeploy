import { Component, EventEmitter, Input, OnChanges, Output, Predicate } from '@angular/core';
import { DeploymentStateDto, InstanceVersionDto, ManifestKey } from '../models/gen.dtos';
import { ProcessService } from '../services/process.service';

@Component({
  selector: 'app-instance-version-card',
  templateUrl: './instance-version-card.component.html',
  styleUrls: ['./instance-version-card.component.css'],
})
export class InstanceVersionCardComponent implements OnChanges {
  @Input() instanceVersionDto: InstanceVersionDto;
  @Input() selected: boolean;
  @Input() dirty: boolean;
  @Input() disabled: boolean;
  @Input() state: DeploymentStateDto;

  @Output() install = new EventEmitter<ManifestKey>();
  @Output() activate = new EventEmitter<ManifestKey>();
  @Output() uninstall = new EventEmitter<ManifestKey>();

  isLoading: boolean;
  isActive: boolean;
  isDeployed: boolean;
  isOffline: boolean;
  isRunning: boolean;

  constructor(private processService: ProcessService) {}

  ngOnChanges() {
    if (!this.state || this.dirty) {
      this.isActive = false;
      this.isDeployed = false;
      this.isOffline = false;
    } else {
      this.isActive = this.state.activatedVersion === this.instanceVersionDto.key.tag;
      this.isDeployed =
        this.state.deployedVersions.findIndex(this.tagPredicate()) !== -1 &&
        this.state.activatedVersion !== this.instanceVersionDto.key.tag;
      this.isOffline = this.state.offlineMasterVersions.findIndex(this.tagPredicate()) !== -1;
    }
  }

  getCardStyle(): string[] {
    const styles: string[] = [];
    if (this.dirty) {
      styles.push('instance-version-modified');
    }
    if (this.disabled) {
      styles.push('instance-version-disabled');
    }
    if (this.selected) {
      styles.push('instance-version-selected');
    }
    return styles;
  }

  tagPredicate(): Predicate<string> {
    return x => x === this.instanceVersionDto.key.tag;
  }

}
