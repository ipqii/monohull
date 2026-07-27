import { ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Typography, Box, Button, Card, CardContent, TextField, Stack, Alert,
  FormControl, InputLabel, Select, MenuItem, Skeleton, List, ListItemButton, ListItemText,
  FormControlLabel, Switch,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBackRounded'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import {
  getImageConfigs, createImageConfig, updateImageConfig, getPipelines,
  exportImageConfigBundle, downloadYaml, ImageConfig, ImageConfigRequest,
} from '../api/client'
import ContainerExtrasEditor from '../components/ContainerExtrasEditor'

const emptyForm: ImageConfigRequest = {
  client: '', project: '', maximoVersion: '', appImage: '', dbImage: '', admImage: '',
  dbVendor: 'DB2', dbName: 'maxdb76', pipelineId: null,
}

const SECTIONS = [
  { id: 'identity', label: 'Identity' },
  { id: 'images', label: 'Images' },
  { id: 'database', label: 'Database' },
  { id: 'storage', label: 'Storage & Paths' },
  { id: 'ports', label: 'Host Ports' },
  { id: 'pipeline', label: 'Pipeline' },
  { id: 'launch', label: 'Launch Defaults' },
  { id: 'extras-db', label: 'DB Extras' },
  { id: 'extras-app', label: 'APP Extras' },
  { id: 'extras-adm', label: 'ADM Extras' },
]

const REQUIRED_FIELDS: { key: keyof ImageConfigRequest; label: string }[] = [
  { key: 'client', label: 'Client' },
  { key: 'project', label: 'Project' },
  { key: 'maximoVersion', label: 'Maximo Version' },
  { key: 'appImage', label: 'App Image' },
  { key: 'dbImage', label: 'DB Image' },
  { key: 'admImage', label: 'ADM Image' },
]

const monoInput = { sx: { fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem' } }

function configToForm(config: ImageConfig): ImageConfigRequest {
  return {
    client: config.client, project: config.project, maximoVersion: config.maximoVersion,
    appImage: config.appImage, dbImage: config.dbImage, admImage: config.admImage,
    dbVendor: config.dbVendor, dbName: config.dbName || undefined,
    dbContainerPort: config.dbContainerPort ?? undefined,
    dbCommand: config.dbCommand || undefined,
    hostVolumePath: config.hostVolumePath || undefined,
    dbVolumeName: config.dbVolumeName || undefined, workspacePath: config.workspacePath || undefined,
    appHttpPort: config.appHttpPort,
    appHttpsPort: config.appHttpsPort,
    dbPort: config.dbPort,
    mockHostPort: config.mockHostPort,
    smtpHostPort: config.smtpHostPort,
    smtpUiHostPort: config.smtpUiHostPort,
    pipelineId: config.pipelineId,
    launchDescription: config.launchDescription || undefined,
    launchStaticPorts: config.launchStaticPorts,
    launchIncludeMock: config.launchIncludeMock,
    launchIncludeSmtp: config.launchIncludeSmtp,
    dbExtraEnv: config.dbExtraEnv ?? [],
    dbExtraBinds: config.dbExtraBinds ?? [],
    appExtraEnv: config.appExtraEnv ?? [],
    appExtraBinds: config.appExtraBinds ?? [],
    admExtraEnv: config.admExtraEnv ?? [],
    admExtraBinds: config.admExtraBinds ?? [],
  }
}

function Section({ id, title, description, children }: {
  id: string; title: string; description?: string; children: ReactNode
}) {
  return (
    <Card id={id} sx={{ scrollMarginTop: 16 }}>
      <CardContent>
        <Typography
          variant="subtitle2"
          sx={{
            color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em',
            fontSize: '0.7rem', borderBottom: '1px solid rgba(148,163,184,0.08)', pb: 0.75, mb: 0.5,
          }}
        >
          {title}
        </Typography>
        {description && (
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
            {description}
          </Typography>
        )}
        <Stack spacing={2.5} sx={{ mt: 2 }}>
          {children}
        </Stack>
      </CardContent>
    </Card>
  )
}

export default function EnvironmentConfigEditPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [form, setForm] = useState<ImageConfigRequest>(emptyForm)
  const [initialized, setInitialized] = useState(!isEdit)
  const initialJson = useRef(JSON.stringify(emptyForm))
  const [activeSection, setActiveSection] = useState(SECTIONS[0].id)

  const { data: configs = [], isLoading } = useQuery({ queryKey: ['imageConfigs'], queryFn: getImageConfigs })
  const { data: pipelines = [] } = useQuery({ queryKey: ['pipelines'], queryFn: getPipelines })

  const config = isEdit ? configs.find(c => c.id === Number(id)) : undefined

  // Initialize the form once the edited config arrives (there is no single-item GET).
  useEffect(() => {
    if (isEdit && config && !initialized) {
      const f = configToForm(config)
      setForm(f)
      initialJson.current = JSON.stringify(f)
      setInitialized(true)
    }
  }, [isEdit, config, initialized])

  const isDirty = initialized && JSON.stringify(form) !== initialJson.current

  // Warn on tab close / hard navigation while there are unsaved changes. In-app sidebar
  // navigation can't be intercepted without a data router; Cancel and the back link confirm.
  useEffect(() => {
    if (!isDirty) return
    const handler = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  // Scroll-spy: highlight the top-most visible section in the side nav.
  useEffect(() => {
    if (!initialized) return
    const observer = new IntersectionObserver(
      entries => {
        const visible = entries.filter(e => e.isIntersecting)
        if (visible.length > 0) {
          const top = visible.reduce((a, b) => (a.boundingClientRect.top < b.boundingClientRect.top ? a : b))
          setActiveSection(top.target.id)
        }
      },
      { rootMargin: '-10% 0px -65% 0px' },
    )
    SECTIONS.forEach(s => {
      const el = document.getElementById(s.id)
      if (el) observer.observe(el)
    })
    return () => observer.disconnect()
  }, [initialized])

  const createMutation = useMutation({
    mutationFn: createImageConfig,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['imageConfigs'] })
      navigate('/config/environments')
    },
  })
  const updateMutation = useMutation({
    mutationFn: (req: ImageConfigRequest) => updateImageConfig(Number(id), req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['imageConfigs'] })
      navigate('/config/environments')
    },
  })
  const isPending = createMutation.isPending || updateMutation.isPending
  const mutationError = (createMutation.error || updateMutation.error) as AxiosError<{ error?: string }> | null

  const missing = useMemo(
    () => REQUIRED_FIELDS.filter(f => !form[f.key]).map(f => f.label),
    [form],
  )

  const handleSave = () => {
    if (missing.length > 0) return
    if (isEdit) updateMutation.mutate(form)
    else createMutation.mutate(form)
  }

  const handleLeave = () => {
    if (isDirty && !window.confirm('Discard unsaved changes?')) return
    navigate('/config/environments')
  }

  const handleExportBundle = async () => {
    try {
      const { filename, yaml } = await exportImageConfigBundle(Number(id))
      downloadYaml(yaml, filename)
    } catch (e) {
      const ax = e as AxiosError<{ error?: string }>
      window.alert('Export failed: ' + (ax.response?.data?.error ?? ax.message))
    }
  }

  const scrollTo = (sectionId: string) => {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  if (isEdit && !isLoading && !config) {
    return (
      <Box>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/config/environments')} sx={{ mb: 2 }}>
          Environments
        </Button>
        <Alert severity="error">Environment config {id} was not found.</Alert>
      </Box>
    )
  }

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, flexWrap: 'wrap', gap: 2, mb: 4 }}>
        <Box>
          <Button
            startIcon={<ArrowBackIcon sx={{ fontSize: '18px !important' }} />}
            onClick={handleLeave}
            size="small"
            sx={{ color: 'text.secondary', mb: 0.5, ml: -1 }}
          >
            Environments
          </Button>
          <Typography variant="h4">
            {isEdit
              ? `Edit Environment${config ? ` — ${config.client}/${config.project}` : ''}`
              : 'New Environment'}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            {isEdit
              ? 'Image template, ports, paths, and container extras for this environment'
              : 'Define the image template new development instances are built from'}
          </Typography>
        </Box>
        {isEdit && (
          <Button variant="outlined" startIcon={<DownloadIcon />} onClick={handleExportBundle}>
            Export Bundle
          </Button>
        )}
      </Box>

      {!initialized ? (
        <Stack spacing={3} sx={{ maxWidth: 860 }}>
          {[1, 2, 3].map(i => <Skeleton key={i} variant="rounded" height={180} sx={{ borderRadius: 3 }} />)}
        </Stack>
      ) : (
        <Box sx={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
          {/* Sticky anchor nav */}
          <Box sx={{ width: 180, flexShrink: 0, position: 'sticky', top: 24, display: { xs: 'none', md: 'block' } }}>
            <List dense disablePadding>
              {SECTIONS.map(s => (
                <ListItemButton
                  key={s.id}
                  selected={activeSection === s.id}
                  onClick={() => scrollTo(s.id)}
                  sx={{ py: 0.75 }}
                >
                  <ListItemText
                    primary={s.label}
                    primaryTypographyProps={{
                      fontSize: '0.82rem',
                      fontWeight: activeSection === s.id ? 600 : 400,
                      color: activeSection === s.id ? '#a5b4fc' : 'text.secondary',
                    }}
                  />
                </ListItemButton>
              ))}
            </List>
          </Box>

          {/* Form sections. All editable inputs share the monospace look (labels and
              helper texts stay in Inter). */}
          <Box
            sx={{
              flex: 1, maxWidth: 860, minWidth: 0,
              '& .MuiInputBase-input': { fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem' },
            }}
          >
            <Stack spacing={3}>
              <Section id="identity" title="Identity">
                <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                  <TextField label="Client" value={form.client} onChange={e => setForm({ ...form, client: e.target.value })} fullWidth required size="small" />
                  <TextField label="Project" value={form.project} onChange={e => setForm({ ...form, project: e.target.value })} fullWidth required size="small" />
                  <FormControl fullWidth required size="small">
                    <InputLabel>Maximo Version</InputLabel>
                    <Select value={form.maximoVersion} label="Maximo Version"
                      onChange={e => setForm({ ...form, maximoVersion: e.target.value })}>
                      <MenuItem value="7.6.1.1">7.6.1.1</MenuItem>
                      <MenuItem value="7.6.1.2">7.6.1.2</MenuItem>
                      <MenuItem value="7.6.1.3">7.6.1.3</MenuItem>
                      <MenuItem value="MAS">MAS</MenuItem>
                    </Select>
                  </FormControl>
                </Box>
              </Section>

              <Section id="images" title="Images" description="Registry references the APP, DB, and ADM containers are created from.">
                <TextField label="App Image" value={form.appImage} onChange={e => setForm({ ...form, appImage: e.target.value })} fullWidth required size="small"
                  placeholder="e.g. registry.example.com/maximo/app:7.6.1.2" InputProps={monoInput} />
                <TextField label="DB Image" value={form.dbImage} onChange={e => setForm({ ...form, dbImage: e.target.value })} fullWidth required size="small"
                  placeholder="e.g. registry.example.com/maximo/db2:7.6.1.2" InputProps={monoInput} />
                <TextField label="ADM Image" value={form.admImage} onChange={e => setForm({ ...form, admImage: e.target.value })} fullWidth required size="small"
                  placeholder="e.g. registry.example.com/maximo/adm:7.6.1.2" InputProps={monoInput} />
              </Section>

              <Section id="database" title="Database">
                <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                  <FormControl fullWidth required size="small">
                    <InputLabel>Database Vendor</InputLabel>
                    <Select value={form.dbVendor} label="Database Vendor"
                      onChange={e => setForm({ ...form, dbVendor: e.target.value })}>
                      <MenuItem value="DB2">DB2</MenuItem>
                      <MenuItem value="ORACLE">Oracle</MenuItem>
                    </Select>
                  </FormControl>
                  <TextField label="Database Name" value={form.dbName || ''} size="small"
                    onChange={e => setForm({ ...form, dbName: e.target.value || undefined })} fullWidth
                    placeholder="maxdb76"
                    helperText="DB2 database name or Oracle service name. Default: maxdb76." />
                  <TextField label="DB Container Port" type="number" value={form.dbContainerPort ?? ''} size="small"
                    onChange={e => setForm({ ...form, dbContainerPort: e.target.value ? Number(e.target.value) : undefined })}
                    fullWidth
                    placeholder="50000 / 1521"
                    helperText="Internal listener port for inter-container JDBC. Defaults: 50000 (DB2) / 1521 (Oracle)." />
                </Box>
                <TextField label="DB Command" value={form.dbCommand || ''} size="small"
                  onChange={e => setForm({ ...form, dbCommand: e.target.value || undefined })} fullWidth
                  placeholder="e.g. restore" InputProps={monoInput}
                  helperText="Argument list passed to the DB image's entrypoint. Some images branch on it to decide whether to restore a database backup — leaving it blank means an empty database. Leave blank for images that ship the database baked in." />
              </Section>

              <Section id="storage" title="Storage & Paths">
                <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                  <TextField label="Host Volume Path" value={form.hostVolumePath || ''} size="small"
                    onChange={e => setForm({ ...form, hostVolumePath: e.target.value || undefined })} fullWidth
                    helperText="Base path on host. Subfolders config/ and logs/ are created per environment." />
                  <TextField label="DB Volume Name" value={form.dbVolumeName || ''} size="small"
                    onChange={e => setForm({ ...form, dbVolumeName: e.target.value || undefined })} fullWidth />
                </Box>
                <TextField label="Workspace Path" value={form.workspacePath || ''} size="small"
                  onChange={e => setForm({ ...form, workspacePath: e.target.value || undefined })} fullWidth
                  placeholder="e.g. /git/repo/LOAMIS" InputProps={monoInput}
                  helperText="Path to local git repository. Mounted as /workspace/{folder_name} in APP and ADM containers." />
              </Section>

              <Section id="ports" title="Static Host Ports"
                description="Optional. Used when an environment is created with the Static Ports switch on. Leave blank to require dynamic allocation.">
                <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                  <TextField label="HTTP Port" type="number" size="small" fullWidth
                    value={form.appHttpPort ?? ''}
                    onChange={e => setForm({ ...form, appHttpPort: e.target.value ? Number(e.target.value) : null })} />
                  <TextField label="HTTPS Port" type="number" size="small" fullWidth
                    value={form.appHttpsPort ?? ''}
                    onChange={e => setForm({ ...form, appHttpsPort: e.target.value ? Number(e.target.value) : null })} />
                  <TextField label="DB Port" type="number" size="small" fullWidth
                    value={form.dbPort ?? ''}
                    onChange={e => setForm({ ...form, dbPort: e.target.value ? Number(e.target.value) : null })} />
                </Box>
                <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                  <TextField label="Mock Host Port" type="number" size="small" fullWidth
                    value={form.mockHostPort ?? ''}
                    onChange={e => setForm({ ...form, mockHostPort: e.target.value ? Number(e.target.value) : null })}
                    helperText="Used when env opts into the mock addon" />
                  <TextField label="SMTP Port" type="number" size="small" fullWidth
                    value={form.smtpHostPort ?? ''}
                    onChange={e => setForm({ ...form, smtpHostPort: e.target.value ? Number(e.target.value) : null })}
                    helperText="Mailpit SMTP host port" />
                  <TextField label="Mailpit UI Port" type="number" size="small" fullWidth
                    value={form.smtpUiHostPort ?? ''}
                    onChange={e => setForm({ ...form, smtpUiHostPort: e.target.value ? Number(e.target.value) : null })}
                    helperText="Web inbox UI host port" />
                </Box>
              </Section>

              <Section id="pipeline" title="Pipeline"
                description="Build pipeline run when an environment of this template is created or rebuilt.">
                <FormControl size="small" sx={{ maxWidth: { sm: 420 } }} fullWidth>
                  <InputLabel>Pipeline</InputLabel>
                  <Select value={form.pipelineId ?? ''} label="Pipeline"
                    onChange={e => setForm({ ...form, pipelineId: e.target.value === '' ? null : Number(e.target.value) })}>
                    <MenuItem value="">None</MenuItem>
                    {pipelines.map(p => (
                      <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Section>

              <Section id="launch" title="Launch Defaults"
                description="What a one-click launch of this profile uses in place of New Build input. Travels with the exported bundle so shared profiles launch the same way everywhere.">
                <TextField label="Profile description" size="small" fullWidth
                  value={form.launchDescription ?? ''}
                  onChange={e => setForm({ ...form, launchDescription: e.target.value || undefined })}
                  helperText='Shown in the profile launcher, e.g. "7.6.1.3 + DB2 + demo data".'
                  inputProps={{ maxLength: 500 }} />
                <FormControlLabel
                  control={<Switch checked={form.launchStaticPorts ?? false}
                    onChange={e => setForm({ ...form, launchStaticPorts: e.target.checked })} />}
                  label="Use static ports (from Host Ports above)" />
                <FormControlLabel
                  control={<Switch checked={form.launchIncludeMock ?? false}
                    onChange={e => setForm({ ...form, launchIncludeMock: e.target.checked })} />}
                  label="Include mock receiver" />
                <FormControlLabel
                  control={<Switch checked={form.launchIncludeSmtp ?? false}
                    onChange={e => setForm({ ...form, launchIncludeSmtp: e.target.checked })} />}
                  label="Include SMTP server (Mailpit)" />
              </Section>

              {/* The extras editors render their own titled, bordered panels — anchor them
                  directly rather than nesting cards. */}
              <Box id="extras-db" sx={{ scrollMarginTop: 16 }}>
                <ContainerExtrasEditor
                  title="DB container extras"
                  envVars={form.dbExtraEnv ?? []}
                  binds={form.dbExtraBinds ?? []}
                  onChange={({ envVars, binds }) => setForm({ ...form, dbExtraEnv: envVars, dbExtraBinds: binds })}
                />
              </Box>
              <Box id="extras-app" sx={{ scrollMarginTop: 16 }}>
                <ContainerExtrasEditor
                  title="APP container extras"
                  envVars={form.appExtraEnv ?? []}
                  binds={form.appExtraBinds ?? []}
                  onChange={({ envVars, binds }) => setForm({ ...form, appExtraEnv: envVars, appExtraBinds: binds })}
                />
              </Box>
              <Box id="extras-adm" sx={{ scrollMarginTop: 16 }}>
                <ContainerExtrasEditor
                  title="ADM container extras"
                  envVars={form.admExtraEnv ?? []}
                  binds={form.admExtraBinds ?? []}
                  onChange={({ envVars, binds }) => setForm({ ...form, admExtraEnv: envVars, admExtraBinds: binds })}
                />
              </Box>
            </Stack>

            {mutationError && (
              <Alert severity="error" sx={{ mt: 3 }}>
                Save failed: {mutationError.response?.data?.error ?? mutationError.message}
              </Alert>
            )}

            {/* Sticky action bar */}
            <Box
              sx={{
                position: 'sticky', bottom: 0, mt: 3, py: 2, px: 3, zIndex: 10,
                display: 'flex', alignItems: 'center', gap: 2,
                background: 'rgba(10, 14, 26, 0.85)', backdropFilter: 'blur(12px)',
                borderTop: '1px solid rgba(99, 102, 241, 0.12)',
                borderRadius: '12px 12px 0 0',
              }}
            >
              <Box sx={{ flex: 1 }}>
                {missing.length > 0 ? (
                  <Typography variant="caption" sx={{ color: '#fbbf24' }}>
                    Required: {missing.join(', ')}
                  </Typography>
                ) : isDirty ? (
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    Unsaved changes
                  </Typography>
                ) : null}
              </Box>
              <Button onClick={handleLeave}>Cancel</Button>
              <Button onClick={handleSave} variant="contained" disabled={isPending || missing.length > 0}>
                {isPending ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Environment'}
              </Button>
            </Box>
          </Box>
        </Box>
      )}
    </>
  )
}
