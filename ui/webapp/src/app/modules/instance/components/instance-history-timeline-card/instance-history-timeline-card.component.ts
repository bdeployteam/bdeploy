import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { HistoryEntryDto, HistoryEntryType, ProcessState } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-history-timeline-card',
  templateUrl: './instance-history-timeline-card.component.html',
  styleUrls: ['./instance-history-timeline-card.component.css']
})
export class InstanceHistoryTimelineCardComponent {

  public highlighted:boolean = false;
  public processState = ProcessState;
  public entryType = HistoryEntryType;

  @Input() entry:HistoryEntryDto;

  @Output() cardOpen: EventEmitter<any> = new EventEmitter();
  @Output() cardClose: EventEmitter<any> = new EventEmitter();
  @Output("add-to-comparison") addToComparison: EventEmitter<any> = new EventEmitter();

  @ViewChild("panel") Item;

  constructor(
    public hostElement: ElementRef,
    ){}

  addToComparisonClick(){
    this.addToComparison.emit(this.entry.instanceTag);
  }

  cardOpened():void{
    this.cardOpen.emit(this);
  }

  cardClosed():void{
    this.cardClose.emit();
  }

  close():void{
    this.Item.close();
  }

  open():void{
    this.Item.open();
  }

  formatDate(time:number):string{
    let date:Date = new Date(time);
    return `${this.toDoubleDigit(date.getDate())}.${this.toDoubleDigit(date.getMonth()+1)}.${date.getFullYear()}  ${date.getHours()}:${this.toDoubleDigit(date.getMinutes())}`;
  }

  toDoubleDigit(number:number){
    return (number.toString().length < 2) ? "0" + number.toString() : number.toString();
  }

  isEmpty(object){
    return Object.keys(object).length == 0;
  }
}

