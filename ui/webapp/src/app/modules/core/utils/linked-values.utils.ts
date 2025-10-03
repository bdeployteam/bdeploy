import {
  ApplicationConfiguration,
  ApplicationDto,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  NodeType,
  OperatingSystem,
  ParameterConfiguration,
  SystemConfiguration,
  VariableConfiguration,
  VariableType,
} from 'src/app/models/gen.dtos';
import { modifyString, StringModificationType } from './parameter-string-modification.utils';
import { getAppOs } from './manifest.utils';

const ARITH_EXPR = `(\\+|-|)?[0-9]+`;

export function createLinkedValue(val: string): LinkedValueConfiguration {
  return !!val && val.indexOf('{{') !== -1
    ? { linkExpression: val, value: null }
    : { linkExpression: null, value: val };
}

export function getPreRenderable(config: LinkedValueConfiguration, type?: VariableType): string {
  if (!config) {
    return '';
  }

  const link = config.linkExpression;
  if (link) {
    return link;
  }

  const value = config.value;
  if (!value) {
    return '';
  }

  if (type === VariableType.PASSWORD) {
    return '*'.repeat(value.length);
  }

  return value;
}

export class LinkVariable {
  name: string;
  description: string;
  preview: string;
  link: string;
  group: string;
  matches?: (s: string) => boolean;
  expand?: (s: string) => string;
}

export function getRenderPreview(
  val: LinkedValueConfiguration,
  process: ApplicationConfiguration,
  instance: InstanceConfigurationDto,
  system: SystemConfiguration
): string {
  if (val.linkExpression) {
    // need to expand. gather all supported expansions for the preview.
    const expansions: LinkVariable[] = [];

    expansions.push(...gatherVariableExpansions(instance, system));
    expansions.push(...gatherProcessExpansions(instance, process));
    expansions.push(...gatherPathExpansions(instance));
    expansions.push(...gatherSpecialExpansions(instance, process, system));

    // now expand the expression
    return expand(val.linkExpression, expansions);
  }
  return val.value;
}

function expand(expression: string, expansions: LinkVariable[]): string {
  let count = 0;

  while (expression) {
    const match = /{{[^}]+}}/.exec(expression);
    if (!match) {
      break; // expression contains no more link variables -> we are done
    }

    const linkVariableId = match[0];
    const linkVariable = expansions.find((v) => (v.matches ? v.matches(linkVariableId) : v.link === linkVariableId));
    if (!linkVariable) {
      return expression; // the link variable in the expression is not known to us -> we cannot expand it -> we return the expression as it currently is
    }

    const replaceWith = linkVariable.expand ? linkVariable.expand(linkVariableId) : linkVariable.preview;
    expression = expression.replace(linkVariableId, replaceWith);

    if (count++ > 20) {
      return expression; // stop after n expansions with the asumption that we ran into a circular reference
    }
  }

  return expression;
}

export function gatherVariableExpansions(
  instance: InstanceConfigurationDto,
  system: SystemConfiguration
): LinkVariable[] {
  const result: LinkVariable[] = [];

  if (system?.systemVariables?.length) {
    system.systemVariables
      .map((v) => {
        return {
          name: v.id,
          description: `System Variable - ${system?.name}`,
          preview: getPreRenderable(v.value, v.type), // explicitly the non-expanded value.
          link: `{{X:${v.id}}}`,
          group: null,
          matches: (s: string) => doMatchVariable(s, v),
          expand: (s: string) => doExpand(s, v.value, v.type),
        } as LinkVariable;
      })
      .forEach((v) => {
        result.push(v);
      });
  }

  if (instance?.config?.instanceVariables?.length) {
    instance?.config.instanceVariables
      .map((v) => {
        return {
          name: v.id,
          description: `Instance Variable - ${instance?.config?.name}`,
          preview: getPreRenderable(v.value, v.type), // explicitly the non-expanded value.
          link: `{{X:${v.id}}}`,
          group: null,
          matches: (s: string) => doMatchVariable(s, v),
          expand: (s: string) => doExpand(s, v.value, v.type),
        } as LinkVariable;
      })
      .forEach((v) => {
        if (result.findIndex((x) => x.name === v.name) < 0) {
          result.push(v);
        }
      });
  }

  return result;
}

export function gatherProcessExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  apps: ApplicationDto[] = []
): LinkVariable[] {
  if (!instance?.nodeDtos?.length) {
    return [];
  }

  const result: LinkVariable[] = [];

  // need to fetch them *directly* as well as through nodes below in case the application has not yet
  // been added to a node (i.e. resolving *while* creating an application).
  if (process?.start?.parameters?.length) {
    for (const param of process.start.parameters) {
      // process parameter, make sure we always use the unqualified version ("This Application")
      processParameter(true, process, param, apps, result);
    }
  }

  for (const node of instance.nodeDtos) {
    for (const app of node.nodeConfiguration.applications) {
      if (node.nodeConfiguration.nodeType === NodeType.CLIENT && app.name === process?.name && app.id !== process?.id) {
        // client app for different OS - this is actually not well supported, we cannot resolve parameters of this.
        continue;
      }
      for (const param of app.start.parameters) {
        if (app.id === process?.id) {
          // we already have the unqualified version in the expansions.
          continue;
        }

        // process parameter, always add full qualified name.
        processParameter(false, app, param, apps, result);
      }
    }
  }
  return result;
}

function processParameter(
  thisApp: boolean,
  app: ApplicationConfiguration,
  param: ParameterConfiguration,
  apps: ApplicationDto[],
  result: LinkVariable[]
) {
  let name: string;
  let description: string;
  let preview: string;
  let link: string;
  let group: string;
  let expandMapper: (s: string) => string;

  if (thisApp) {
    link = `{{V:${param.id}}}`;
    group = `${app.name} (This Application)`;
  } else {
    link = `{{V:${app.name}:${param.id}}}`;
    group = app.name;
  }

  const appDesc = apps?.find((a) => a.key.name === app.application.name)?.descriptor;
  if (appDesc) {
    // process value according to type is possible and required.
    for (const paramDesc of appDesc.startCommand.parameters) {
      if (paramDesc.id === param.id) {
        name = paramDesc.name;
        description = paramDesc.longDescription;
        preview = getPreRenderable(param.value, paramDesc.type);
        expandMapper = (s: string) => doExpand(s, param.value, paramDesc.type);
        break;
      }
    }

    // if we failed to find a descriptor for the parameter (i.e. name is still not set), this is a custom parameter
    // and we need to populate the fields with what we have.
    if (!name) {
      name = param.id;
      description = `Custom parameter ${name}`;
      preview = getPreRenderable(param.value);
      expandMapper = (s: string) => doExpand(s, param.value, VariableType.STRING); // custom is always string.
    }
  } else {
    // just for display - this is OK as value and expression, no need to expand.
    name = param.id;
    description = '';
    preview = getPreRenderable(param.value);
    expandMapper = (s: string) => doExpand(s, param.value);
  }

  result.push({
    name: name,
    description: description,
    preview: preview,
    link: link,
    group: group,
    matches: (s: string) => doMatchProcessParameter(s, param.id, app.name, thisApp),
    expand: expandMapper,
  });
}

function doMatchVariable(s: string, config: VariableConfiguration): boolean {
  return (
    s &&
    config &&
    (s === `{{X:${config.id}}}` ||
      ([VariableType.NUMERIC, VariableType.CLIENT_PORT, VariableType.SERVER_PORT].includes(config.type) &&
        s.match(`{{X:${config.id}:${ARITH_EXPR}}}`) != null))
  );
}

function doMatchProcessParameter(s: string, paramId: string, appName: string, thisApp: boolean): boolean {
  if (!s || !paramId) {
    return false;
  }
  if (thisApp) {
    return s === `{{V:${paramId}}}` || s.match(`{{V:${paramId}:${ARITH_EXPR}}}`) != null;
  }
  return s === `{{V:${appName}:${paramId}}}` || s.match(`{{V:${appName}:${paramId}:${ARITH_EXPR}}}`) != null;
}

function doExpand(wholeExpression: string, config: LinkedValueConfiguration, paramType?: VariableType): string {
  if (!wholeExpression || !config) {
    return '';
  }

  const linkExpression = config.linkExpression;
  if (linkExpression) {
    return linkExpression;
  }

  const value = config.value;
  if (!value) {
    return '';
  }

  const inner = wholeExpression.substring(1 + wholeExpression.indexOf(':'), wholeExpression.length - '}}'.length);
  const lastColonIndex = inner.lastIndexOf(':');
  const valueAsNumber = Number(value);
  const attemptArithmethic = valueAsNumber && lastColonIndex !== -1;

  if (paramType) {
    return attemptArithmethic &&
      [VariableType.NUMERIC, VariableType.SERVER_PORT, VariableType.CLIENT_PORT].includes(paramType)
      ? doAttemptArithmetic(inner, lastColonIndex, valueAsNumber) || getPreRenderable(config, paramType)
      : getPreRenderable(config, paramType);
  }

  return attemptArithmethic
    ? doAttemptArithmetic(inner, lastColonIndex, valueAsNumber) || getPreRenderable(config)
    : getPreRenderable(config);
}

function doAttemptArithmetic(inner: string, lastColonIndex: number, valueAsNumber: number): string {
  const otherNumber = Number(inner.substring(lastColonIndex + 1));
  if (otherNumber) {
    return String(valueAsNumber + otherNumber);
  }
  return null;
}

export function gatherPathExpansions(instance: InstanceConfigurationDto): LinkVariable[] {
  const result: LinkVariable[] = [];
  result.push({
    name: 'P:ROOT',
    description: 'The root directory of the instance version installation.',
    preview: `/deploy/${getInstanceIdOrMock(instance)}`,
    link: '{{P:ROOT}}',
    group: null,
  });
  result.push({
    name: 'P:CONFIG',
    description: 'Resolved path to the configuration folder.',
    preview: `/deploy/${getInstanceIdOrMock(instance)}/bin/<tag>/config`,
    link: '{{P:CONFIG}}',
    group: null,
  });
  result.push({
    name: 'P:RUNTIME',
    description: 'The directory where version specific runtime information is stored.',
    preview: `/deploy/${getInstanceIdOrMock(instance)}/bin/<tag>/runtime`,
    link: '{{P:RUNTIME}}',
    group: null,
  });
  result.push({
    name: 'P:BIN',
    description: 'The directory where binaries are installed without pooling (per instance version).',
    preview: `/deploy/${getInstanceIdOrMock(instance)}/bin/<tag>`,
    link: '{{P:BIN}}',
    group: null,
  });
  result.push({
    name: 'P:DATA',
    description: 'The directory where applications can place version independant data.',
    preview: `/deploy/${getInstanceIdOrMock(instance)}/data`,
    link: '{{P:DATA}}',
    group: null,
  });
  result.push({
    name: 'P:LOG_DATA',
    description: 'The logging directory which may be outside the usual scope of the application.',
    preview: `/log_data/${getInstanceIdOrMock(instance)}`,
    link: '{{P:LOG_DATA}}',
    group: null,
  });
  result.push({
    name: 'P:MANIFEST_POOL',
    description: 'The directory where globally pooled applications are installed.',
    preview: `/deploy/pool`,
    link: '{{P:MANIFEST_POOL}}',
    group: null,
  });
  result.push({
    name: 'P:INSTANCE_MANIFEST_POOL',
    description: 'The directory where instance-locally pooled applications are installed.',
    preview: `/deploy/${getInstanceIdOrMock(instance)}/pool`,
    link: '{{P:INSTANCE_MANIFEST_POOL}}',
    group: null,
  });
  return result;
}

function getInstanceIdOrMock(instance: InstanceConfigurationDto): string {
  return instance?.config?.id || 'xxxx-xxx-xxxx';
}

export function gatherSpecialExpansions(
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration
): LinkVariable[] {
  const result: LinkVariable[] = [];

  // Manifest
  result.push({
    name: 'M',
    description: 'The resolved path to a specified manifest.',
    preview: '/deploy/pool/example_1.0.0',
    link: '{{M:<manifest-name>}}',
    group: null,
    matches: (s) => s.startsWith('{{M:') && s.endsWith('}}'),
  });

  // Environment
  result.push({
    name: 'ENV',
    description: 'The resolved environment variable value.',
    preview: '<env-value>',
    link: '{{ENV:<env-name>}}',
    group: null,
    matches: (s) => s.startsWith('{{ENV:') && s.endsWith('}}'),
  });
  result.push({
    name: 'DELAYED',
    description:
      'Delays another link until starting the application (instead of installation), e.g. {{DELAYED:ENV:PATH}}.',
    preview: '<delayed-value>',
    link: '{{DELAYED:<link-expression>}}',
    group: null,
    matches: (s) => s.startsWith('{{DELAYED:') && s.endsWith('}}'),
  });

  // Instance
  result.push({
    name: 'I:SYSTEM_PURPOSE',
    description: `The instance's configured purpose`,
    preview: instance?.config?.purpose ? instance.config.purpose : '<system-purpose>',
    link: '{{I:SYSTEM_PURPOSE}}',
    group: null,
  });
  result.push({
    name: 'I:NAME',
    description: `The instance name`,
    preview: instance?.config?.name ? instance.config.name : '<instance-name>',
    link: '{{I:NAME}}',
    group: null,
  });
  result.push({
    name: 'I:ID',
    description: `The instance ID`,
    preview: getInstanceIdOrMock(instance),
    link: '{{I:ID}}',
    group: null,
  });
  result.push({
    name: 'I:UUID',
    description: `The instance ID`,
    preview: getInstanceIdOrMock(instance),
    link: '{{I:UUID}}',
    group: null,
  });
  result.push({
    name: 'I:TAG',
    description: 'The instance version',
    preview: 'X',
    link: '{{I:TAG}}',
    group: null,
  });
  result.push({
    name: 'I:PRODUCT_ID',
    description: `The instance's configured product ID`,
    preview: instance?.config?.product?.name ? instance.config.product.name : '<product-id>',
    link: '{{I:PRODUCT_ID}}',
    group: null,
  });
  result.push({
    name: 'I:PRODUCT_TAG',
    description: `The instance's configured product version`,
    preview: instance?.config?.product?.tag ? instance.config.product.tag : '<product-tag>',
    link: '{{I:PRODUCT_TAG}}',
    group: null,
  });
  result.push({
    name: 'I:DEPLOYMENT_INFO_FILE',
    description: `The path to a file containing the instance's serialized deployment configuration.`,
    preview: '/deploy/instance/deployment-info.json',
    link: '{{I:DEPLOYMENT_INFO_FILE}}',
    group: null,
  });

  // Application
  result.push({
    name: 'A:NAME',
    description: `The application name`,
    preview: process ? process.name : '<application-name>',
    link: '{{A:NAME}}',
    group: null,
  });
  result.push({
    name: 'A:ID',
    description: `The application ID`,
    preview: process ? process.id : '<application-id>',
    link: '{{A:ID}}',
    group: null,
  });
  result.push({
    name: 'A:UUID',
    description: `The application ID`,
    preview: process ? process.id : '<application-id>',
    link: '{{A:UUID}}',
    group: null,
  });

  // Minion
  result.push({
    name: 'H:HOSTNAME',
    description:
      'The hostname of the machine executing this application. In case of client applications, this is the hostname of the client PC.',
    preview: '<hostname>',
    link: '{{H:HOSTNAME}}',
    group: null,
  });

  // Operating System
  result.push({
    name: 'WINDOWS',
    description: 'Expands to the provided value only in case of the target node running Windows.',
    preview: '<windows-value>',
    link: '{{WINDOWS:<windows-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{WINDOWS:') && s.endsWith('}}'),
    expand: (s) =>
      process
        ? getAppOs(process.application) === OperatingSystem.WINDOWS
          ? s.substring('{{WINDOWS:'.length, s.indexOf('}}'))
          : ''
        : '',
  });
  result.push({
    name: 'LINUX',
    description: 'Expands to the provided value only in case of the target node running Linux.',
    preview: '<linux-value>',
    link: '{{LINUX:<linux-value>}}',
    group: null,
    matches: (s) => s.startsWith('{{LINUX:') && s.endsWith('}}'),
    expand: (s) =>
      process
        ? getAppOs(process.application) === OperatingSystem.LINUX
          ? s.substring('{{LINUX:'.length, s.indexOf('}}'))
          : ''
        : '',
  });

  // Logical
  result.push({
    name: 'IF',
    description:
      'Conditionally expands to the first (true) or second (false) value depending on the result of the given condition expression.',
    preview: '<result>',
    link: '{{IF:condition?valueIfTrue:valueIfFalse}}',
    group: null,
    matches: (s) =>
      s.startsWith('{{IF:') && s.endsWith('}}') && s.split('?').length === 2 && s.split('?')[1].split(':').length === 2,
    expand: (s) => expandCondition(s, instance, process, system),
  });

  // File URI
  result.push({
    name: 'FILEURI',
    description: 'Transforms the given path into a file URI',
    preview: '<fileuri>',
    link: '{{FILEURI:<fileuri>}}',
    group: null,
    matches: (s) => s.startsWith('{{FILEURI:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(StringModificationType.FILEURI, s, instance, process, system),
  });

  // Escaping
  result.push({
    name: 'XML',
    description: 'Escapes special XML characters',
    preview: '<xml>',
    link: '{{XML:<xml>}}',
    group: null,
    matches: (s) => s.startsWith('{{XML:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(StringModificationType.XML, s, instance, process, system),
  });
  result.push({
    name: 'JSON',
    description: 'Escapes special JSON characters',
    preview: '<json>',
    link: '{{JSON:<json>}}',
    group: null,
    matches: (s) => s.startsWith('{{JSON:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(StringModificationType.JSON, s, instance, process, system),
  });
  result.push({
    name: 'YAML',
    description: 'Escapes special YAML characters',
    preview: '<yaml>',
    link: '{{YAML:<yaml>}}',
    group: null,
    matches: (s) => s.startsWith('{{YAML:') && s.endsWith('}}'),
    expand: (s) => expandEscapeSpecialCharacters(StringModificationType.YAML, s, instance, process, system),
  });

  return result;
}

function expandEscapeSpecialCharacters(
  type: StringModificationType,
  s: string,
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration
): string {
  const prefix = `{{${type}:`;
  if (!s.startsWith(prefix) || !s.endsWith('}}')) {
    return s; // malformed, don't expand
  }
  const expr = s.substring(prefix.length, s.indexOf('}}'));
  let unescaped = getRenderPreview(createLinkedValue(`{{${expr}}}`), process, instance, system);
  if (unescaped.startsWith('{{') && unescaped.endsWith('}}')) {
    unescaped = unescaped.substring('{{'.length, unescaped.indexOf('}}'));
  }
  return modifyString(type, unescaped);
}

function expandCondition(
  condition: string,
  instance: InstanceConfigurationDto,
  process: ApplicationConfiguration,
  system: SystemConfiguration
) {
  const match = /{{IF:([^?]+)\?([^:]*):(.*)}}/.exec(condition);

  if (!match) {
    return condition; // no match, expression malformed? just don't expand it...
  }

  const expr = match[1];
  const valIfTrue = match[2];
  const valIfFalse = match[3];

  const exprResult = getRenderPreview(createLinkedValue(`{{${expr}}}`), process, instance, system);

  if (exprResult !== null && exprResult !== undefined && exprResult.trim() !== '' && exprResult.trim() !== 'false') {
    return valIfTrue;
  }
  return valIfFalse;
}
