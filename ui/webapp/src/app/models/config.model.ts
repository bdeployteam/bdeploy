import { LogLevel } from '../services/logging.service';
import { MinionMode } from './gen.dtos';

export interface AppConfig {
  api: string;
  logLevel: LogLevel;
  mode: MinionMode;
}
