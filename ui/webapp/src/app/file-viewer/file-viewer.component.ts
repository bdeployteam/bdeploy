import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceDirectoryEntry, StringEntryChunkDto } from '../models/gen.dtos';
import { InstanceService } from '../services/instance.service';

const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-file-viewer',
  templateUrl: './file-viewer.component.html',
  styleUrls: ['./file-viewer.component.css']
})
export class FileViewerComponent implements OnInit, OnDestroy {

  @Input() title: String;
  @Input() initialEntry: () => Observable<InstanceDirectoryEntry>;
  @Input() contentFetcher: (offset: number, length: number) => Observable<StringEntryChunkDto>;
  @Input() follow = false;

  @Output() closeEvent = new EventEmitter<void>();

  @ViewChild('contentDiv') private contentDiv: ElementRef;

  content: String = '';

  private timer;
  private offset = 0;

  constructor(private instanceService: InstanceService) { }

  ngOnInit() {
    // fetch initial content tail...
    this.loadInitial();
    this.onToggleFollow(this.follow);
  }

  loadInitial() {
    this.content = '';
    this.initialEntry().subscribe(entry => {
      if (entry == null) {
        this.content = 'File not found';
        return;
      }
      let offset = 0;
      if (entry.size > MAX_TAIL) {
        offset = entry.size - MAX_TAIL;
      }
      this.offset = offset;
      this.updateTail();
    });
  }

  ngOnDestroy() {
    this.clearTimer();
  }

  onToggleFollow(checked: boolean) {
    this.follow = checked;
    if (checked) {
      this.timer = setInterval(() => this.updateTail(), 1000);
    } else {
      this.clearTimer();
    }
  }

  private clearTimer() {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  updateTail() {
    this.contentFetcher(this.offset, 0).subscribe(chunk => {
      if (chunk == null) {
        // no changes.
        return;
      }

      this.content += chunk.content;
      this.offset = chunk.endPointer;

      this.scrollToBottom();
    });
  }

  private scrollToBottom() {
    // follow is enabled if timer is set.
    if (this.timer) {
      setTimeout(() => this.contentDiv.nativeElement.scrollTop = this.contentDiv.nativeElement.scrollHeight, 50);
    }
  }

}
