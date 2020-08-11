import { animate, state, style, transition, trigger } from '@angular/animations';
import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HistoryEntryDto, HistoryEntryVersionDto, InstanceConfiguration } from 'src/app/models/gen.dtos';
import { LoggingService } from 'src/app/modules/core/services/logging.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { InstanceHistoryTimelineComponent } from 'src/app/modules/instance/components/instance-history-timeline/instance-history-timeline.component';
import { InstanceService } from '../../services/instance.service';

@Component({
  selector: 'app-instance-history',
  templateUrl: './instance-history.component.html',
  styleUrls: ['./instance-history.component.css'],
  animations:[trigger("fade",[
    state("void",style({opacity:0})),
    transition("void <=> *",[
      animate("0.05s")
    ])
  ])
  ]
})
export class InstanceHistoryComponent implements OnInit{

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');

  loadedAll:boolean = false;
  loading:boolean = false;

  showCreateEvents = true;
  showConfigEvents = true;
  showRuntimeEvents = false;

  currentOffset:number = 0;
  amount:number = 10;

  accordionBehaviour = false;

  instance: InstanceConfiguration;
  historyEntries:HistoryEntryDto[];
  unfilteredEntries:HistoryEntryDto[];

  compareVersions:Number[] = [,];
  compareDialogOpen:boolean = false;
  compareDialogContent:HistoryEntryVersionDto;

  @ViewChild("searchInput") searchInput;
  @ViewChild("timeline") timeline:InstanceHistoryTimelineComponent;

  @ViewChild("compare_a") compareInputA;
  @ViewChild("compare_b") compareInputB;



  constructor(
    private route: ActivatedRoute,
    private instanceService:InstanceService,
    public location:Location,
    private loggingService:LoggingService,
    public routingHistoryService:RoutingHistoryService,
    ) {
    }

  ngOnInit(): void {
    this.instanceService.getInstance(this.groupParam,this.uuidParam).subscribe((val)=>this.instance = val);
    this.loadHistory();
  }

  closeAll():void{
    this.timeline.closeAll();
  }

  loadHistory():void{
    this.loading = true;
    this.instanceService.getInstanceHistory(this.groupParam,this.uuidParam,this.amount).subscribe((list:HistoryEntryDto[])=>{
      this.unfilteredEntries = list;
      this.filter();
      this.loading = false;
      if(list.length < this.amount){
        this.loadedAll = true;
      }
      this.currentOffset += this.amount
    });
  }

  loadMoreHistory():void{
    if(!this.loading){
      this.loading = true;
    this.instanceService.getMoreInstanceHistory(this.groupParam,this.uuidParam,this.amount,this.currentOffset).subscribe((list:HistoryEntryDto[])=>{
      this.unfilteredEntries = this.unfilteredEntries.concat(list);
      this.filter();
      this.loading = false;
      if(list.length < this.amount){
        this.loadedAll = true;
      }
      this.currentOffset += this.amount
    });
    }
  }

  onScrolledDown():void{

    if(!this.loadedAll){
      this.loadMoreHistory();
    }
  }

  addVersionToCompare(version:number):void{
    this.compareDialogOpen = false;

    if(this.compareVersions.indexOf(version) != -1){
      return;
    }
    if(!this.compareVersions[0]){
      this.compareVersions[0] = version;
      this.compareInputA.nativeElement.value = version.toString();
    }
    else{
      if(!this.compareVersions[1]){
        this.compareVersions[1] = version;
      }
      else{
        this.compareVersions[0] = this.compareVersions[1];
        this.compareVersions[1] = version;
      }

      if(this.compareVersions[0] < this.compareVersions[1]){
        this.compareInputA.nativeElement.value = this.compareVersions[0].toString();
        this.compareInputB.nativeElement.value = version.toString();
      }
      else{
        this.compareInputA.nativeElement.value = this.compareVersions[1].toString();
        this.compareInputB.nativeElement.value = this.compareVersions[0].toString();
      }
    }

  }

  compareInputKeydown(event:KeyboardEvent,index):void{
    this.compareDialogOpen = false;
    if(this.isNumeric(event.key)){
      this.compareVersions[index] = parseInt(event.key);
    }

    else if(event.key=="Enter"){
      this.compareVersion();
    }
  }

  isNumeric(number:string):boolean{
    return !isNaN(parseInt(number));
  }

  compareVersion():void{

    let valueA:string = this.compareInputA.nativeElement.value;
    let valueB:string = this.compareInputB.nativeElement.value;

    this.instanceService.listInstanceVersions(this.groupParam,this.uuidParam).subscribe((ret)=>{
        let amount = ret.length;

        if(valueA=="" || valueB == "" || valueA == null || valueB == null){
          this.loggingService.guiError("please select a version");

        }
        else if(!this.isNumeric(valueA) || !this.isNumeric(valueB)){
          this.loggingService.guiError("please use a valid number");

        }
        else if(parseInt(valueA) == parseInt(valueB)){
          this.loggingService.guiError("you can't compare the same versions");
        }
        else if(parseInt(valueA) <= 0 || parseInt(valueB) <= 0 || parseInt(valueA) > amount || parseInt(valueB) > amount){
          this.loggingService.guiError("please select a version between 1 and " + amount,);
        }
        else{
          if(parseInt(valueA) > parseInt(valueB)){
            this.compareInputA.nativeElement.value = valueB;
            this.compareInputB.nativeElement.value = valueA;
            valueA = valueB;
            valueB = this.compareInputB.nativeElement.value;
          }
            this.instanceService.getVersionComparison(this.groupParam,this.uuidParam,parseInt(valueA),parseInt(valueB)).subscribe((ret)=>{
              this.compareDialogOpen = true;
              this.compareDialogContent = ret;
            });
        }
    });
  }
  isEmpty(object){
    return Object.keys(object).length == 0;
  }

  filter():void{
    this.historyEntries = this.unfilteredEntries.filter(item => item.type=="CREATE" && this.showCreateEvents || item.type=="CONFIG" && this.showConfigEvents || item.type=="RUNTIME" && this.showRuntimeEvents);
    setTimeout(()=>{
      if(!this.timeline.isOverflowing() && !this.loadedAll){
        this.loadMoreHistory();
      }
    },0);
  }
}
