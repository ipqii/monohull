import yaml from 'js-yaml'
import { CustomAction, CreateCustomActionRequest, ImageConfig, EnvironmentResponse } from '../api/client'

/** Serialize a saved action for export, with human-readable scope comments. */
export function actionToYaml(a: CustomAction, imageConfigs: ImageConfig[], environments: EnvironmentResponse[]): string {
  const obj: Record<string, unknown> = {
    name: a.name,
    targetRole: a.targetRole,
    executionType: a.executionType || 'EXEC',
    command: a.command,
  }
  if (a.description) obj.description = a.description
  if (a.workingDir) obj.workingDir = a.workingDir
  if (a.timeoutSeconds != null) obj.timeoutSeconds = a.timeoutSeconds
  if (a.allowedExitCodes) obj.allowedExitCodes = a.allowedExitCodes
  if (a.runAsUser) obj.runAsUser = a.runAsUser
  if (a.imageConfigId != null) {
    const ic = imageConfigs.find(c => c.id === a.imageConfigId)
    obj.imageConfigId = a.imageConfigId
    if (ic) obj['# imageConfig'] = `${ic.client} / ${ic.project}`
  }
  if (a.environmentId != null) {
    const env = environments.find(e => e.id === a.environmentId)
    obj.environmentId = a.environmentId
    if (env) obj['# environment'] = env.name
  }
  if (a.autoRun) obj.autoRun = true
  return yaml.dump(obj, { lineWidth: -1, quotingType: '"', forceQuotes: false })
}

/** Serialize the in-progress form for the YAML editing mode. */
export function formToYaml(form: CreateCustomActionRequest, imageConfigs: ImageConfig[], environments: EnvironmentResponse[]): string {
  const obj: Record<string, unknown> = {
    name: form.name || '',
    targetRole: form.targetRole || 'ADM',
    executionType: form.executionType || 'EXEC',
    command: form.command || '',
  }
  if (form.description) obj.description = form.description
  if (form.workingDir) obj.workingDir = form.workingDir
  if (form.timeoutSeconds != null) obj.timeoutSeconds = form.timeoutSeconds
  if (form.allowedExitCodes) obj.allowedExitCodes = form.allowedExitCodes
  if (form.runAsUser) obj.runAsUser = form.runAsUser
  if (form.imageConfigId != null) {
    const ic = imageConfigs.find(c => c.id === form.imageConfigId)
    obj.imageConfigId = form.imageConfigId
    if (ic) obj['# imageConfig'] = `${ic.client} / ${ic.project}`
  }
  if (form.environmentId != null) {
    const env = environments.find(e => e.id === form.environmentId)
    obj.environmentId = form.environmentId
    if (env) obj['# environment'] = env.name
  }
  if (form.autoRun) obj.autoRun = true
  return yaml.dump(obj, { lineWidth: -1, quotingType: '"', forceQuotes: false })
}

/** Parse the YAML editing mode back into the form shape. Throws on invalid YAML. */
export function yamlToForm(text: string): CreateCustomActionRequest {
  const obj = yaml.load(text) as Record<string, unknown>
  if (!obj || typeof obj !== 'object') throw new Error('Invalid YAML')
  return {
    name: String(obj.name ?? ''),
    description: obj.description ? String(obj.description) : undefined,
    targetRole: String(obj.targetRole ?? 'ADM'),
    command: String(obj.command ?? ''),
    workingDir: obj.workingDir ? String(obj.workingDir) : undefined,
    timeoutSeconds: obj.timeoutSeconds != null ? Number(obj.timeoutSeconds) : undefined,
    imageConfigId: obj.imageConfigId != null ? Number(obj.imageConfigId) : undefined,
    environmentId: obj.environmentId != null ? Number(obj.environmentId) : undefined,
    autoRun: obj.autoRun === true,
    executionType: obj.executionType ? String(obj.executionType) : 'EXEC',
    allowedExitCodes: obj.allowedExitCodes ? String(obj.allowedExitCodes) : undefined,
    runAsUser: obj.runAsUser ? String(obj.runAsUser) : undefined,
  }
}

export type ScopeValue = 'global' | `image:${number}` | `env:${number}`

export function scopeFromForm(form: CreateCustomActionRequest): ScopeValue {
  if (form.environmentId != null) return `env:${form.environmentId}` as ScopeValue
  if (form.imageConfigId != null) return `image:${form.imageConfigId}` as ScopeValue
  return 'global'
}

export function applyScopeToForm(form: CreateCustomActionRequest, scope: ScopeValue): CreateCustomActionRequest {
  if (scope === 'global') return { ...form, imageConfigId: undefined, environmentId: undefined }
  if (scope.startsWith('env:')) return { ...form, imageConfigId: undefined, environmentId: Number(scope.slice(4)) }
  return { ...form, imageConfigId: Number(scope.slice(6)), environmentId: undefined }
}
