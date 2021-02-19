import { Component, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
  BdDataGroupingDefinition,
  bdExtractGroups,
} from 'src/app/models/data';

interface TestRow {
  type: string;
  first: string;
  second?: string;
  action: string;
  server: string;
  load: number;
  logo?: string;
}

@Component({
  selector: 'app-groups-browser',
  templateUrl: './groups-browser.component.html',
  styleUrls: ['./groups-browser.component.css'],
})
export class GroupsBrowserComponent implements OnInit {
  columns: BdDataColumn<TestRow>[] = [
    {
      id: 'type',
      name: 'Type',
      display: BdDataColumnDisplay.CARD,
      hint: BdDataColumnTypeHint.TYPE,
      data: (r) => r.type,
    },
    {
      id: 'first',
      name: 'First',
      hint: BdDataColumnTypeHint.TITLE,
      data: (r) => r.first,
    },
    {
      id: 'second',
      name: 'Second',
      description: 'This is a super duper column',
      showWhen: '(min-width:1280px)',
      hint: BdDataColumnTypeHint.DESCRIPTION,
      data: (r) => r.second,
    },
    {
      id: 'detail1',
      name: 'Server',
      data: (r) => r.server,
      icon: (r) => 'dns',
      width: '60px',
      hint: BdDataColumnTypeHint.DETAILS,
      display: BdDataColumnDisplay.BOTH,
    },
    {
      id: 'detail2',
      name: 'Load',
      data: (r) => r.load,
      icon: (r) => 'timeline',
      width: '150px',
      hint: BdDataColumnTypeHint.DETAILS,
      display: BdDataColumnDisplay.TABLE,
    },
    {
      id: 'detail3',
      name: 'Load',
      data: (r) => r.load,
      icon: (r) => 'timeline',
      width: '150px',
      hint: BdDataColumnTypeHint.DETAILS,
      display: BdDataColumnDisplay.BOTH,
    },
    {
      id: 'avatar',
      name: 'Avatar',
      data: (r) => r.logo,
      hint: BdDataColumnTypeHint.AVATAR,
      display: BdDataColumnDisplay.BOTH,
    },
    {
      id: 'footer',
      name: 'Footer',
      data: (r) => `${r.first} - ${r.second}: ${r.server}`,
      hint: BdDataColumnTypeHint.FOOTER,
      display: BdDataColumnDisplay.CARD,
    },
    {
      id: 'action',
      name: 'Click Me!',
      hint: BdDataColumnTypeHint.ACTIONS,
      data: (r) => r.action,
      icon: (r) => r.action,
      action: (r) => {
        console.log('1 CLICKED!', r);
      },
      width: '50px',
      display: BdDataColumnDisplay.TABLE,
    },
    {
      id: 'action2',
      name: 'Click Me!',
      hint: BdDataColumnTypeHint.ACTIONS,
      data: (r) => r.action,
      icon: (r) => r.action,
      action: (r) => {
        console.log('2 CLICKED!', r);
      },
      width: '50px',
      display: BdDataColumnDisplay.BOTH,
    },
  ];

  rows = new BehaviorSubject<TestRow[]>([
    { type: 'Development', first: 'The', second: 'First', action: 'settings', server: 'Test Server', load: 0.35 },
    {
      type: 'Productive',
      first: 'The',
      second: 'Second',
      action: 'help',
      server: 'Another Server',
      load: 0.12,
      logo: 'https://localhost:7701/api/group/T/image?logo=406e8d2431209ffa2445adcdea1309fca6bd0b0b',
    },
    {
      type: 'Test',
      first: 'Aaaand',
      action: 'dns',
      server: 'More Server',
      load: 1.23,
      logo: 'https://localhost:7701/api/group/X/image?logo=5119d721c51852ab70c3b511c22ae23b7b3bf211',
    },
  ]);

  definitions: BdDataGroupingDefinition<TestRow>[] = [
    { name: 'First Column', group: (r) => r.first },
    { name: 'Second Column', group: (r) => r.second },
    { name: 'Server Column', group: (r) => r.server },
  ];

  boundGetGroupingValues = this.getGroupingValues.bind(this);

  checked: TestRow[] = [this.rows.value[1]];

  testValues = [
    'below-left',
    'below-right',
    'above-left',
    'above-right',
    'left-above',
    'left-below',
    'right-above',
    'right-below',
  ];

  sort(data: TestRow[], column: BdDataColumn<TestRow>, direction: 'asc' | 'desc') {
    if (direction === 'asc') {
      return data.sort((a, b) => String(column.data(a)).localeCompare(column.data(b)));
    } else {
      return data.sort((a, b) => String(column.data(b)).localeCompare(column.data(a)));
    }
  }

  constructor() {}

  ngOnInit(): void {}

  addRows() {
    const r = [...this.rows.value];
    let i = 10;
    while (i--) {
      r.push({ type: 'Generated', first: 'Another', second: 'Row1', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'Another', second: 'Row2', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'Another', second: 'Row3', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'Another', second: 'Row4', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'Another', second: 'Row5', action: 'build', server: 'Server', load: 1.92 });

      r.push({ type: 'Generated', first: 'ZZ', second: 'Row1', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'ZZ', second: 'Row2', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'ZZ', second: 'Row3', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'ZZ', second: 'Row4', action: 'build', server: 'Server', load: 1.92 });
      r.push({ type: 'Generated', first: 'ZZ', second: 'Row5', action: 'build', server: 'Server', load: 1.92 });
    }

    this.rows.next(r);
  }

  onRecordClick(row: TestRow) {
    console.log('CLICK: ', row);
  }

  onCheckChange() {
    console.log('CHECKED', this.checked);
  }

  getGroupingValues(def: BdDataGroupingDefinition<TestRow>): string[] {
    return bdExtractGroups(def, this.rows.value);
  }
}
