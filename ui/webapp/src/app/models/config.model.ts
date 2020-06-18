import { LogLevel } from '../modules/core/services/logging.service';
import { MinionMode, Version } from './gen.dtos';

export interface AppConfig {
  api: string;
  logLevel: LogLevel;
  mode: MinionMode;
  version: Version;
}

export interface StatusMessage {
  icon: string;
  message: string;
}
