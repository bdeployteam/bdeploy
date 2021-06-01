import { Base64 } from 'js-base64';
import { HistoryEntryDto, HistoryEntryType } from 'src/app/models/gen.dtos';

export class HistoryKey {
  ts: number;
  tp: HistoryEntryType;
  tag: string;
}

export function histKey(dto: HistoryEntryDto): HistoryKey {
  return { ts: dto.timestamp, tp: dto.type, tag: dto.instanceTag };
}

export function histKeyEncode(key: HistoryKey) {
  return Base64.encode(JSON.stringify(key));
}

export function histKeyDecode(encoded: string): HistoryKey {
  return JSON.parse(Base64.decode(encoded));
}
