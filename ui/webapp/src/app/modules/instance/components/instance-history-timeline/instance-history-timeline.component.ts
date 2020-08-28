import { Component, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, ViewChildren } from '@angular/core';
import { HistoryEntryDto, HistoryEntryType, ProcessState } from "../../../../models/gen.dtos";
import { InstanceHistoryTimelineCardComponent } from '../instance-history-timeline-card/instance-history-timeline-card.component';

@Component({
  selector: 'app-instance-history-timeline',
  templateUrl: './instance-history-timeline.component.html',
  styleUrls: ['./instance-history-timeline.component.css']
})
export class InstanceHistoryTimelineComponent implements OnInit {

  @ViewChildren("timeline_card") cards;

  @Output("scrolled-down") scrolledDown:EventEmitter<any> = new EventEmitter();
  @Output("add-to-comparison") addToComparison:EventEmitter<any> = new EventEmitter();

  @Input("entry-list") entries:HistoryEntryDto[] = [];
  @Input("accordion-behaviour") accordionBehaviour = false;

  openedItem:InstanceHistoryTimelineCardComponent;
  containSearchInput:InstanceHistoryTimelineCardComponent[];

  processState = ProcessState;
  entryType = HistoryEntryType;

  scrollThreshold = 1; // The distance to the bottom doesn't always become 0 when scrolled down -> threshold when to say scrolled down

  constructor(private hostElement:ElementRef) { }

  ngOnInit(): void {
  }

  cardOpened(item:InstanceHistoryTimelineCardComponent):void{
    if(this.openedItem && this.openedItem != item && this.accordionBehaviour){
      this.openedItem.close();
    }
    this.openedItem = item;
  }

  cardClosed():void{
    this.openedItem = undefined;
  }

  closeAll():void{
    for(let card of this.cards._results){
      card.close();
    }
  }

  @HostListener("scroll",["event"])
  onScroll(){
    if(this.hostElement.nativeElement.scrollHeight - this.hostElement.nativeElement.scrollTop - this.hostElement.nativeElement.clientHeight < this.scrollThreshold){
      this.scrolledDown.emit();
    }
  }

  formatDate(time:number):string{
    let date:Date = new Date(time);
    return `${this.toDoubleDigit(date.getDate())}.${this.toDoubleDigit(date.getMonth()+1)}.${date.getFullYear()}  ${date.getHours()}:${this.toDoubleDigit(date.getMinutes())}`;
  }

  toDoubleDigit(number:number){
    return (number.toString().length < 2) ? "0" + number.toString() : number.toString();
  }

  addToComparisonEvent(event){
    this.addToComparison.emit(event);
  }

  isOverflowing():boolean{
    if(this.entries.length > 0){
      return this.hostElement.nativeElement.scrollHeight > this.hostElement.nativeElement.clientHeight;
    }else{
      return false;
    }
  }
}
