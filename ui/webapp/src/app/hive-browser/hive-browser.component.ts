import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ConfigService } from '../services/config.service';
import { HiveService } from '../services/hive.service';
import { Logger, LoggingService } from '../services/logging.service';

@Component({
  selector: 'app-hive-browser',
  templateUrl: './hive-browser.component.html',
  styleUrls: ['./hive-browser.component.css']
})
export class HiveBrowserComponent implements OnInit {

  log: Logger = this.loggingService.getLogger('HiveBrowserComponent');

  hiveKeys: string[] = [];
  selectedHive: string;

  constructor(private http: HttpClient, private cfg: ConfigService, private hiveService: HiveService,
    private route: ActivatedRoute, private loggingService: LoggingService) { }

  ngOnInit(): void {
    this.log.debug('initalizing...');

    this.route.paramMap.subscribe(params => {
    this.selectedHive = params.get('hive');
    });

    this.hiveService.listHives().subscribe(
      keys => {
        this.log.debug('got ' + keys.length + ' hives');
        this.hiveKeys = keys;
        if (this.selectedHive == null && keys.length > 0) {
          this.selectedHive = keys[0];
        }
      }
    );
  }

}
