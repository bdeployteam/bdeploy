import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Logger, LoggingService } from '../../../../core/services/logging.service';
import { HiveService } from '../../services/hive.service';

@Component({
  selector: 'app-hive-browser',
  templateUrl: './hive-browser.component.html',
  styleUrls: ['./hive-browser.component.css'],
})
export class HiveBrowserComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('HiveBrowserComponent');

  hiveKeys: string[] = [];
  selectedHive: string;

  constructor(private hiveService: HiveService, private route: ActivatedRoute, private loggingService: LoggingService) {}

  ngOnInit(): void {
    this.log.debug('initalizing...');

    this.route.paramMap.subscribe((params) => {
      this.selectedHive = params.get('hive');
    });

    this.hiveService.listHives().subscribe((keys) => {
      this.log.debug('got ' + keys.length + ' hives');
      this.hiveKeys = keys;
      if (this.selectedHive == null && keys.length > 0) {
        this.selectedHive = keys[0];
      }
    });
  }
}
