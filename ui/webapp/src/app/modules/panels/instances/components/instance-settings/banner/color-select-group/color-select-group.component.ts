import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { ColorSelectComponent } from '../color-select/color-select.component';

export interface ColorDef {
  name: string;
  fg: string;
  bg: string;
}

@Component({
  selector: 'app-color-select-group',
  templateUrl: './color-select-group.component.html',
  styleUrls: ['./color-select-group.component.css'],
})
export class ColorSelectGroupComponent implements OnInit {
  @Output() colorChanged = new EventEmitter<ColorDef>();

  /* template */ colors = [
    { name: 'Highlight 1', fg: '#FFFFFF', bg: '#C2185B' },
    { name: 'Highlight 2', fg: '#FFFFFF', bg: '#1A237E' },
    { name: 'Highlight 3', fg: '#FFFFFF', bg: '#2F729E' },
    { name: 'Highlight 4', fg: '#FFFFFF', bg: '#8A007C' },
    { name: 'Positive', fg: '#FFFFFF', bg: '#2E7D32' },
    { name: 'Warning', fg: '#000000', bg: '#FF6D00' },
    { name: 'Critical', fg: '#FFFFFF', bg: '#EA000A' },
  ];
  /* template */ selected;

  constructor() {}

  ngOnInit(): void {}

  public setDefault() {
    this.selected = this.colors[0].name;
    this.colorChanged.emit({
      name: this.colors[0].name,
      fg: this.colors[0].fg,
      bg: this.colors[0].bg,
    });
  }

  public trySetSelected(fg: string, bg: string) {
    const sel = this.colors.find((c) => c.fg === fg && c.bg === bg);
    if (sel) {
      this.selected = sel.name;
      this.colorChanged.emit({
        name: sel.name,
        fg: sel.fg,
        bg: sel.bg,
      });
    }
  }

  /* template */ onChange(sel: ColorSelectComponent) {
    this.selected = sel.name;
    this.colorChanged.emit({
      name: sel.name,
      fg: sel.fg,
      bg: sel.bg,
    });
  }
}
