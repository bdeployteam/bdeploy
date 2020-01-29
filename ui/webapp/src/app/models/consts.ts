import { ApplicationConfiguration, ApplicationStartType, CommandConfiguration, InstanceConfiguration, InstanceGroupConfiguration, InstanceNodeConfiguration, InstanceNodeConfigurationDto, InstancePurpose, InstanceStateRecord, InstanceVersionDto, ManifestKey, ParameterConfiguration, ParameterDescriptor, ParameterType, ProcessControlConfiguration, SoftwareRepositoryConfiguration, UserChangePasswordDto, UserInfo } from './gen.dtos';

// HTTP header constant used to suppress global error handling
export const NO_ERROR_HANDLING_HDR = 'X-No-Global-Error-Handling';

// Defines the order in which instances should appear based on their purpose
const PURPOSE_ORDER = [InstancePurpose.PRODUCTIVE, InstancePurpose.TEST, InstancePurpose.DEVELOPMENT];

// Function to sort a purpose by the internal index
// Used in the instance browser to determine the order
export const SORT_PURPOSE = (a: InstancePurpose, b: InstancePurpose) => {
  const aIdx = PURPOSE_ORDER.findIndex(x => x === a);
  const bIdx = PURPOSE_ORDER.findIndex(x => x === b);
  if (aIdx < bIdx) {
    return -1;
  }
  if (aIdx > bIdx) {
    return 1;
  }
  return 0;
};

export const CLIENT_NODE_NAME = '__ClientApplications';

export const EMPTY_MANIFEST_KEY: ManifestKey = {
  name: null,
  tag: null,
};

export const EMPTY_USER_INFO: UserInfo = {
  name: null,
  password: null,
  fullName: null,
  email: null,
  external: false,
  externalSystem: null,
  externalTag: null,
  inactive: false,
  lastActiveLogin: null,
  capabilities: [],
  recentlyUsedInstanceGroups: []
};

export const EMPTY_USER_CHANGE_PASSWORD_DTO: UserChangePasswordDto  = {
  user: null,
  currentPassword: null,
  newPassword: null
}


export const EMPTY_INSTANCE: InstanceConfiguration = {
  uuid: null,
  name: null,
  description: null,
  purpose: null,
  configTree: null,
  autoStart: false,
  product: EMPTY_MANIFEST_KEY,
  autoUninstall: null
};

export const EMPTY_INSTANCE_GROUP: InstanceGroupConfiguration = {
  name: null,
  description: null,
  logo: null,
  autoDelete: null,
  managed: false
};

export const EMPTY_SOFTWARE_REPO: SoftwareRepositoryConfiguration = {
  name: null,
  description: null,
};

export const EMPTY_COMMAND_CONFIGURATION: CommandConfiguration = {
  executable: null,
  parameters: [],
};

export const EMPTY_PARAMETER_CONFIGURATION: ParameterConfiguration = {
  uid: null,
  value: null,
  preRendered: [],
};

export const EMPTY_PARAMETER_DESCRIPTOR: ParameterDescriptor = {
  uid: null,
  defaultValue: null,
  fixed: false,
  global: false,
  groupName: null,
  hasValue: false,
  longDescription: null,
  mandatory: false,
  name: null,
  parameter: null,
  type: ParameterType.STRING,
  valueAsSeparateArg: false,
  valueSeparator: null,
};

export const EMPTY_DEPLOYMENT_STATE: InstanceStateRecord = {
  activeTag: null,
  lastActiveTag: null,
  installedTags: []
};

export const EMPTY_APPLICATION_CONFIGURATION: ApplicationConfiguration = {
  uid: null,
  name: null,
  application: null,
  processControl: null,
  start: EMPTY_COMMAND_CONFIGURATION,
  stop: EMPTY_COMMAND_CONFIGURATION,
  endpoints: { http: [] }
};

export const EMPTY_INSTANCE_NODE_CONFIGURATION: InstanceNodeConfiguration = {
  uuid: null,
  autoStart: false,
  purpose: null,
  product: null,
  name: null,
  applications: [],
};

export const EMPTY_INSTANCE_NODE_CONFIGURATION_DTO: InstanceNodeConfigurationDto = {
  nodeName: null,
  nodeConfiguration: null,
  foreignNodeConfigurations: null,
};

export const EMPTY_VERSION_DTO: InstanceVersionDto = {
  key: null,
  product: null,
};

export const EMPTY_PROCESS_CONTROL_CONFIG: ProcessControlConfiguration = {
  gracePeriod: 30,
  keepAlive: false,
  noOfRetries: 5,
  startType: ApplicationStartType.MANUAL,
};
