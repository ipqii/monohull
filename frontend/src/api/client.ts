import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
})

// Global 401 handling. The AuthProvider registers a handler that clears the
// session so the route guard bounces to /login. Auth bootstrap endpoints
// (/auth/me, /auth/login) handle their own 401s and are skipped here.
let unauthorizedHandler: (() => void) | null = null
export const setUnauthorizedHandler = (fn: (() => void) | null) => {
  unauthorizedHandler = fn
}

api.interceptors.response.use(
  r => r,
  err => {
    const url: string = err.config?.url ?? ''
    if (err.response?.status === 401 && !url.includes('/auth/')) {
      unauthorizedHandler?.()
    }
    return Promise.reject(err)
  },
)

export interface ImageConfig {
  id: number
  client: string
  project: string
  maximoVersion: string
  appImage: string
  dbImage: string
  admImage: string
  dbVendor: string
  dbName: string | null
  dbContainerPort: number | null
  dbCommand: string | null
  hostVolumePath: string | null
  dbVolumeName: string | null
  workspacePath: string | null
  appHttpPort: number | null
  appHttpsPort: number | null
  dbPort: number | null
  mockHostPort: number | null
  smtpHostPort: number | null
  smtpUiHostPort: number | null
  pipelineId: number | null
  pipelineName: string | null
  launchDescription: string | null
  launchStaticPorts: boolean
  launchIncludeMock: boolean
  launchIncludeSmtp: boolean
  createdAt: string
  dbExtraEnv: ExtraEnvVar[] | null
  dbExtraBinds: ExtraBind[] | null
  appExtraEnv: ExtraEnvVar[] | null
  appExtraBinds: ExtraBind[] | null
  admExtraEnv: ExtraEnvVar[] | null
  admExtraBinds: ExtraBind[] | null
}

export interface ExtraEnvVar {
  key: string
  value: string
}

export interface ExtraBind {
  hostPath: string
  containerPath: string
  readOnly: boolean
}

export interface ImageConfigRequest {
  client: string
  project: string
  maximoVersion: string
  appImage: string
  dbImage: string
  admImage: string
  dbVendor: string
  dbName?: string
  dbContainerPort?: number
  dbCommand?: string
  hostVolumePath?: string
  dbVolumeName?: string
  workspacePath?: string
  appHttpPort?: number | null
  appHttpsPort?: number | null
  dbPort?: number | null
  mockHostPort?: number | null
  smtpHostPort?: number | null
  smtpUiHostPort?: number | null
  pipelineId?: number | null
  launchDescription?: string | null
  launchStaticPorts?: boolean
  launchIncludeMock?: boolean
  launchIncludeSmtp?: boolean
  dbExtraEnv?: ExtraEnvVar[]
  dbExtraBinds?: ExtraBind[]
  appExtraEnv?: ExtraEnvVar[]
  appExtraBinds?: ExtraBind[]
  admExtraEnv?: ExtraEnvVar[]
  admExtraBinds?: ExtraBind[]
}

export interface ContainerState {
  state: string
  running: boolean
  startedAt: string | null
  finishedAt: string | null
}

export interface ContainerResponse {
  id: number
  containerName: string
  dockerContainerId: string | null
  role: string
  image: string
  ports: string | null
  status: string
  liveState: ContainerState | null
}

/**
 * Badge status for a container, preferring the live Docker state over the
 * persisted lifecycle status. When liveState is present it is authoritative
 * (a stopped container must not show the stale persisted RUNNING); only when
 * it is absent — container not yet created, or inspect unavailable — do we
 * fall back to the persisted status.
 */
export function containerBadgeStatus(c: ContainerResponse): string {
  if (!c.liveState) return c.status
  if (c.liveState.running) return 'RUNNING'
  return c.liveState.state === 'removed' ? 'REMOVED' : 'STOPPED'
}

export interface EnvironmentResponse {
  id: number
  name: string
  buildId: string
  maximoVersion: string
  dbVendor: string
  dbName: string
  status: string
  createdAt: string
  updatedAt: string
  publicUrl: string | null
  containers: ContainerResponse[]
}

export interface CreateEnvironmentRequest {
  name: string
  imageConfigId: number
  staticPorts: boolean
  appHttpPort?: number
  appHttpsPort?: number
  dbPort?: number
  includeMock?: boolean
  mockHostPort?: number
  includeSmtp?: boolean
  smtpHostPort?: number
  smtpUiHostPort?: number
}

export interface ConfigResponse {
  id: number
  hostVolumePath: string | null
  dbVolumeName: string | null
  staticPorts: boolean
  appHttpPort: number | null
  appHttpsPort: number | null
  dbPort: number | null
  dbPassword: string | null
  dbCommand: string | null
  dbExtraEnv: ExtraEnvVar[] | null
  dbExtraBinds: ExtraBind[] | null
  appExtraEnv: ExtraEnvVar[] | null
  appExtraBinds: ExtraBind[] | null
  admExtraEnv: ExtraEnvVar[] | null
  admExtraBinds: ExtraBind[] | null
  pipelineDefinitionId: number | null
}

export interface LogLine {
  line: string
  timestamp: string
}

export interface LogHistoryPage {
  total: number
  offset: number
  limit: number
  lines: LogLine[]
}

export interface ActionDefinition {
  id: string
  name: string
  description: string
  targetRole: string
  builtIn: boolean
  customActionId: number | null
  afterAction: string | null
  autoRun: boolean
  executionType: string
  runAsUser: string | null
  imageConfigId: number | null
  environmentId: number | null
}

export interface ActionExecution {
  executionId: string
  actionKey: string
  status: string
  environmentId: number
  containerId: number
  startedAt: string | null
  finishedAt: string | null
  exitCode: number | null
  pipelineRunId: string | null
  sequenceOrder: number | null
}

export interface ExecuteActionRequest {
  actionId: string
  containerId: number
}

export interface CreateCustomActionRequest {
  name: string
  description?: string
  targetRole: string
  command: string
  workingDir?: string
  timeoutSeconds?: number
  imageConfigId?: number | null
  environmentId?: number | null
  autoRun?: boolean
  executionType?: string
  allowedExitCodes?: string
  runAsUser?: string
}

export interface CustomAction {
  id: number
  actionKey: string
  name: string
  description: string | null
  targetRole: string
  command: string
  workingDir: string | null
  timeoutSeconds: number
  imageConfigId: number | null
  environmentId: number | null
  createdAt: string
  afterAction: string | null
  autoRun: boolean
  builtIn: boolean
  executionType: string
  allowedExitCodes: string | null
  runAsUser: string | null
}

export interface PipelineStep {
  order: number
  actionId: string
  actionName: string
  targetRole: string
  status: string
  executionId: string
  startedAt: string | null
  finishedAt: string | null
  exitCode: number | null
}

export interface PipelineStatus {
  pipelineRunId: string | null
  status: string
  steps: PipelineStep[]
}

// Image configs
export const getImageConfigs = () => api.get<ImageConfig[]>('/config/images').then(r => r.data)
export const getNextSequence = (client: string, project: string) =>
  api.get<{ nextSequence: number }>('/config/images/next-sequence', { params: { client, project } }).then(r => r.data.nextSequence)
export const createImageConfig = (req: ImageConfigRequest) => api.post<ImageConfig>('/config/images', req).then(r => r.data)
export const updateImageConfig = (id: number, req: ImageConfigRequest) => api.put<ImageConfig>(`/config/images/${id}`, req).then(r => r.data)
export const deleteImageConfig = (id: number) => api.delete(`/config/images/${id}`)

// Bundle export/import (image config + linked pipeline + custom actions referenced by the pipeline)
export interface BundleImportResult {
  imageConfig: 'CREATED' | 'UPDATED' | 'NONE'
  imageConfigId: number
  pipeline: 'CREATED' | 'UPDATED' | 'NONE'
  pipelineId: number | null
  createdActionKeys: string[]
  updatedActionKeys: string[]
}

export interface BundleConflictResponse {
  error: string
  conflicts: string[]
}

/** Trigger a browser download of YAML content (used with exportImageConfigBundle). */
export function downloadYaml(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/yaml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

const FILENAME_RE = /filename="?([^";]+)"?/i

export const exportImageConfigBundle = async (id: number): Promise<{ filename: string; yaml: string }> => {
  const r = await api.get<string>(`/config/images/${id}/export`, {
    responseType: 'text',
    transformResponse: [(data) => data],
  })
  const cd = (r.headers['content-disposition'] || r.headers['Content-Disposition'] || '') as string
  const match = FILENAME_RE.exec(cd)
  const filename = match ? match[1] : `image-config-${id}.bundle.yaml`
  return { filename, yaml: r.data }
}

export const importImageConfigBundle = (yaml: string, overwrite: boolean) =>
  api.post<BundleImportResult>('/config/import', yaml, {
    headers: { 'Content-Type': 'application/x-yaml' },
    params: { overwrite },
  }).then(r => r.data)

// Profiles (one-click launches, MXF-20)

export interface ProfileLaunchResult {
  importResult: BundleImportResult | null
  importSkipped: boolean | null
  environment: EnvironmentResponse
}

/** Launch a stored profile: an image config plus its saved launch defaults. */
export const launchProfile = (imageConfigId: number) =>
  api.post<ProfileLaunchResult>(`/profiles/${imageConfigId}/launch`).then(r => r.data)

/** Import a shared bundle YAML (skipped when its template already exists) and launch it. */
export const launchProfileBundle = (yaml: string, overwrite: boolean) =>
  api.post<ProfileLaunchResult>('/profiles/launch', yaml, {
    headers: { 'Content-Type': 'application/x-yaml' },
    params: { overwrite },
  }).then(r => r.data)

// Environments
export const getEnvironments = () => api.get<EnvironmentResponse[]>('/environments').then(r => r.data)
export const getEnvironment = (id: number) => api.get<EnvironmentResponse>(`/environments/${id}`).then(r => r.data)
export const createEnvironment = (req: CreateEnvironmentRequest) => api.post<EnvironmentResponse>('/environments', req).then(r => r.data)
export const deleteEnvironment = (id: number) => api.delete(`/environments/${id}`)
export const stopEnvironment = (id: number) => api.post(`/environments/${id}/stop`)
export const startEnvironment = (id: number) => api.post(`/environments/${id}/start`)

export interface SetPasswordResult { success: boolean; output: string }
export const setMaximoUserPassword = (envId: number, loginId: string, password: string) =>
  api.post<SetPasswordResult>(`/environments/${envId}/maximo-user-password`, { loginId, password }).then(r => r.data)

export const getLogHistory = (id: number, params?: { offset?: number; limit?: number }) =>
  api.get<LogHistoryPage>(`/environments/${id}/logs/history`, { params }).then(r => r.data)
export const getConfig = (id: number) => api.get<ConfigResponse>(`/environments/${id}/config`).then(r => r.data)
export const updateConfig = (id: number, config: Partial<ConfigResponse>) => api.put<ConfigResponse>(`/environments/${id}/config`, config).then(r => r.data)
export const setEnvironmentPipeline = (id: number, pipelineDefinitionId: number | null) =>
  api.put<ConfigResponse>(`/environments/${id}/pipeline`, { pipelineDefinitionId }).then(r => r.data)

// Containers
export const restartContainer = (id: number) => api.post(`/containers/${id}/restart`)
export const stopContainer = (id: number) => api.post(`/containers/${id}/stop`)
export const startContainer = (id: number) => api.post(`/containers/${id}/start`)
export const getContainerLogs = (id: number, tail = 500) =>
  api.get<string[]>(`/containers/${id}/logs`, { params: { tail } }).then(r => r.data)

// Actions
export const getActions = (envId: number) => api.get<ActionDefinition[]>(`/environments/${envId}/actions`).then(r => r.data)
export const executeAction = (envId: number, req: ExecuteActionRequest) => api.post<ActionExecution>(`/environments/${envId}/actions/execute`, req).then(r => r.data)
export const getActionHistory = (envId: number) => api.get<ActionExecution[]>(`/environments/${envId}/actions/history`).then(r => r.data)
export const getExecutionLogHistory = (executionId: string) => api.get<LogLine[]>(`/actions/executions/${executionId}/logs/history`).then(r => r.data)

// Custom actions config
export const getCustomActions = () => api.get<CustomAction[]>('/config/actions').then(r => r.data)
export const createCustomAction = (req: CreateCustomActionRequest) => api.post<CustomAction>('/config/actions', req).then(r => r.data)
export const updateCustomAction = (id: number, req: CreateCustomActionRequest) => api.put<CustomAction>(`/config/actions/${id}`, req).then(r => r.data)
export const deleteCustomAction = (id: number) => api.delete(`/config/actions/${id}`)

// Pipeline
export const startPipeline = (envId: number) => api.post(`/environments/${envId}/pipeline/start`)
export const getPipelineStatus = (envId: number) => api.get<PipelineStatus>(`/environments/${envId}/pipeline/status`).then(r => r.data)

// Pipeline definitions
export interface PipelineDefinition {
  id: number
  name: string
  description: string | null
  environmentId: number | null
  steps: PipelineStepDetail[]
  createdAt: string
  updatedAt: string
}

export interface PipelineStepDetail {
  id: number
  actionKey: string
  actionName: string
  targetRole: string
  sequenceOrder: number
}

export interface CreatePipelineRequest {
  name: string
  description?: string
  environmentId?: number | null
  steps: { actionKey: string; sequenceOrder: number }[]
}

export const getPipelines = () => api.get<PipelineDefinition[]>('/config/pipelines').then(r => r.data)
export const getPipeline = (id: number) => api.get<PipelineDefinition>(`/config/pipelines/${id}`).then(r => r.data)
export const createPipeline = (req: CreatePipelineRequest) => api.post<PipelineDefinition>('/config/pipelines', req).then(r => r.data)
export const updatePipeline = (id: number, req: CreatePipelineRequest) => api.put<PipelineDefinition>(`/config/pipelines/${id}`, req).then(r => r.data)
export const deletePipeline = (id: number) => api.delete(`/config/pipelines/${id}`)

// Registry credentials (private docker registry)
export interface RegistryCredential {
  id: number
  url: string
  username: string
  hasPassword: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface RegistryCredentialRequest {
  url: string
  username: string
  password?: string
  description?: string
}

export const getRegistryCredential = () =>
  api.get<RegistryCredential>('/config/registry', { validateStatus: s => s === 200 || s === 204 })
    .then(r => (r.status === 204 ? null : r.data))
export const saveRegistryCredential = (req: RegistryCredentialRequest) =>
  api.put<RegistryCredential>('/config/registry', req).then(r => r.data)
export const deleteRegistryCredential = () => api.delete('/config/registry')

// Browsing the registry contents (MH-20)
export interface RegistryCatalog {
  registry: string
  repositories: string[]
  truncated: boolean
}

export interface RegistryTags {
  repository: string
  tags: string[]
}

export const getRegistryCatalog = () =>
  api.get<RegistryCatalog>('/config/registry/catalog').then(r => r.data)
export const getRegistryTags = (repository: string) =>
  api.get<RegistryTags>('/config/registry/tags', { params: { repository } }).then(r => r.data)

// Auth
export interface AuthUser {
  username: string
  roles: string
}

export const login = (username: string, password: string) => {
  const body = new URLSearchParams({ username, password })
  return api.post('/auth/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
}
export const logout = () => api.post('/auth/logout')
export const getMe = () => api.get<AuthUser>('/auth/me').then(r => r.data)

// Connected repositories + PR builds
export type RepoProvider = 'GITHUB' | 'BITBUCKET' | 'GITLAB'
export type RepoBuildMode = 'BUILD_ONLY' | 'BUILD_AND_ENV'
export type RepoAuthMethod = 'HTTPS' | 'SSH'

export interface ConnectedRepository {
  id: number
  name: string
  provider: RepoProvider
  authMethod: RepoAuthMethod
  repoUrl: string
  repoFullName: string
  defaultBranch: string
  buildMode: RepoBuildMode
  imageConfigId: number | null
  imageConfigName: string | null
  webhookSecret: string
  webhookUrl: string
  cloneUsername: string | null
  hasCloneToken: boolean
  hasSshKey: boolean
  hasStatusToken: boolean
  maxConcurrent: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ConnectedRepositoryRequest {
  name: string
  provider: RepoProvider
  authMethod?: RepoAuthMethod
  repoUrl: string
  repoFullName: string
  defaultBranch?: string
  buildMode: RepoBuildMode
  imageConfigId: number
  cloneUsername?: string
  cloneToken?: string
  sshPrivateKey?: string
  sshPassphrase?: string
  statusToken?: string
  maxConcurrent?: number
  enabled?: boolean
}

export interface PRBuild {
  id: number
  repositoryId: number
  prNumber: number
  prTitle: string | null
  sourceBranch: string
  targetBranch: string | null
  commitSha: string | null
  event: string
  status: string
  buildId: string
  environmentId: number | null
  error: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

export const getRepositories = () =>
  api.get<ConnectedRepository[]>('/config/repositories').then(r => r.data)
export const getRepository = (id: number) =>
  api.get<ConnectedRepository>(`/config/repositories/${id}`).then(r => r.data)
export const createRepository = (req: ConnectedRepositoryRequest) =>
  api.post<ConnectedRepository>('/config/repositories', req).then(r => r.data)
export const updateRepository = (id: number, req: ConnectedRepositoryRequest) =>
  api.put<ConnectedRepository>(`/config/repositories/${id}`, req).then(r => r.data)
export const deleteRepository = (id: number) => api.delete(`/config/repositories/${id}`)
export const getPRBuilds = (repoId: number) =>
  api.get<PRBuild[]>(`/config/repositories/${repoId}/pr-builds`).then(r => r.data)

export default api
