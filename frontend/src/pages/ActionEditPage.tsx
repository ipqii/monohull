import { ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  Typography, Box, Button, Card, CardContent, TextField, Stack, Alert, Chip,
  FormControl, InputLabel, Select, MenuItem, Skeleton, List, ListItemButton, ListItemText,
  FormControlLabel, Switch, ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBackRounded'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import {
  getCustomActions, createCustomAction, updateCustomAction,
  getImageConfigs, getEnvironments, CustomAction, CreateCustomActionRequest,
} from '../api/client'
import { formToYaml, yamlToForm, scopeFromForm, applyScopeToForm, ScopeValue } from '../utils/actionYaml'

const emptyForm: CreateCustomActionRequest = {
  name: '', targetRole: 'ADM', command: '', executionType: 'EXEC',
}

const SECTIONS = [
  { id: 'definition', label: 'Definition' },
  { id: 'execution', label: 'Execution' },
  { id: 'behaviour', label: 'Behaviour' },
  { id: 'scope', label: 'Scope' },
]

const REQUIRED_FIELDS: { key: keyof CreateCustomActionRequest; label: string }[] = [
  { key: 'name', label: 'Name' },
  { key: 'targetRole', label: 'Target Role' },
  { key: 'command', label: 'Command' },
]

const monoInput = { fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem', lineHeight: 1.6 }

function actionToForm(action: CustomAction, asClone: boolean): CreateCustomActionRequest {
  return {
    name: asClone ? `${action.name} (copy)` : action.name,
    description: action.description || undefined,
    targetRole: action.targetRole,
    command: action.command,
    workingDir: action.workingDir || undefined,
    timeoutSeconds: action.timeoutSeconds,
    imageConfigId: action.imageConfigId ?? undefined,
    environmentId: action.environmentId ?? undefined,
    autoRun: action.autoRun,
    executionType: action.executionType || 'EXEC',
    allowedExitCodes: action.allowedExitCodes || undefined,
    runAsUser: action.runAsUser || undefined,
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

export default function ActionEditPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const cloneSource = (location.state as { from?: CustomAction } | null)?.from

  const [form, setForm] = useState<CreateCustomActionRequest>(
    !isEdit && cloneSource ? actionToForm(cloneSource, true) : emptyForm,
  )
  const [initialized, setInitialized] = useState(!isEdit)
  const initialJson = useRef(JSON.stringify(!isEdit && cloneSource ? actionToForm(cloneSource, true) : emptyForm))
  const [activeSection, setActiveSection] = useState(SECTIONS[0].id)
  const [mode, setMode] = useState<'form' | 'yaml'>('form')
  const [yamlText, setYamlText] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const { data: actions = [], isLoading } = useQuery({ queryKey: ['customActions'], queryFn: getCustomActions })
  const { data: imageConfigs = [] } = useQuery({ queryKey: ['imageConfigs'], queryFn: getImageConfigs })
  const { data: environments = [] } = useQuery({ queryKey: ['environments'], queryFn: getEnvironments })

  const action = isEdit ? actions.find(a => a.id === Number(id)) : undefined

  // Initialize the form once the edited action arrives (there is no single-item GET).
  useEffect(() => {
    if (isEdit && action && !initialized) {
      const f = actionToForm(action, false)
      setForm(f)
      initialJson.current = JSON.stringify(f)
      setInitialized(true)
    }
  }, [isEdit, action, initialized])

  const isDirty = initialized && (
    mode === 'yaml' || JSON.stringify(form) !== initialJson.current
  )

  useEffect(() => {
    if (!isDirty) return
    const handler = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  // Scroll-spy: highlight the top-most visible section in the side nav.
  useEffect(() => {
    if (!initialized || mode !== 'form') return
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
  }, [initialized, mode])

  const createMutation = useMutation({
    mutationFn: createCustomAction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customActions'] })
      navigate('/config/actions')
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      setFormError(err.response?.data?.message || err.message || 'Failed to create action')
    },
  })
  const updateMutation = useMutation({
    mutationFn: (req: CreateCustomActionRequest) => updateCustomAction(Number(id), req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customActions'] })
      navigate('/config/actions')
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      setFormError(err.response?.data?.message || err.message || 'Failed to update action')
    },
  })
  const isSaving = createMutation.isPending || updateMutation.isPending

  const missing = useMemo(
    () => (mode === 'form' ? REQUIRED_FIELDS.filter(f => !form[f.key]).map(f => f.label) : []),
    [form, mode],
  )

  const handleModeSwitch = (_: unknown, newMode: 'form' | 'yaml' | null) => {
    if (!newMode || newMode === mode) return
    setFormError(null)
    if (newMode === 'yaml') {
      setYamlText(formToYaml(form, imageConfigs, environments))
    } else {
      try {
        setForm(yamlToForm(yamlText))
      } catch {
        setFormError('Invalid YAML. Fix errors before switching to form view.')
        return
      }
    }
    setMode(newMode)
  }

  const handleSave = () => {
    let submitted = form
    if (mode === 'yaml') {
      try {
        submitted = yamlToForm(yamlText)
      } catch {
        setFormError('Invalid YAML.')
        return
      }
    }
    if (!submitted.name || !submitted.targetRole || !submitted.command) {
      setFormError('Name, Target Role, and Command are required.')
      return
    }
    setFormError(null)
    if (isEdit) updateMutation.mutate(submitted)
    else createMutation.mutate(submitted)
  }

  const handleLeave = () => {
    if (isDirty && !window.confirm('Discard unsaved changes?')) return
    navigate('/config/actions')
  }

  const scrollTo = (sectionId: string) => {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  if (isEdit && !isLoading && !action) {
    return (
      <Box>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/config/actions')} sx={{ mb: 2 }}>
          Actions
        </Button>
        <Alert severity="error">Action {id} was not found.</Alert>
      </Box>
    )
  }

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, flexWrap: 'wrap', gap: 2, mb: 3 }}>
        <Box>
          <Button
            startIcon={<ArrowBackIcon sx={{ fontSize: '18px !important' }} />}
            onClick={handleLeave}
            size="small"
            sx={{ color: 'text.secondary', mb: 0.5, ml: -1 }}
          >
            Actions
          </Button>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="h4">
              {isEdit ? `Edit Action${action ? ` — ${action.name}` : ''}` : 'New Action'}
            </Typography>
            {action?.builtIn && (
              <Chip label="Built-in" size="small" sx={{ fontSize: '0.7rem', height: 22, bgcolor: 'rgba(34,211,238,0.1)', color: '#67e8f9', border: 'none' }} />
            )}
          </Box>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            {isEdit ? 'Command, execution settings, and scope for this action' : 'Define a command to run on environment containers'}
          </Typography>
        </Box>
        <ToggleButtonGroup
          value={mode}
          exclusive
          onChange={handleModeSwitch}
          size="small"
          sx={{
            '& .MuiToggleButton-root': {
              textTransform: 'none', fontWeight: 500, fontSize: '0.8rem', px: 1.5, py: 0.25,
              borderColor: 'rgba(99,102,241,0.2)',
              '&.Mui-selected': { bgcolor: 'rgba(99,102,241,0.12)', color: '#818cf8' },
            },
          }}
        >
          <ToggleButton value="form">Form</ToggleButton>
          <ToggleButton value="yaml">YAML</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {action?.builtIn && (
        <Alert severity="info" sx={{ mb: 3, maxWidth: 1064 }}>
          This is a built-in action. You can edit it for live experiments, but built-ins resync
          from <code>application.yml</code> at every Monohull restart — lasting changes belong in the yml.
        </Alert>
      )}

      {formError && <Alert severity="error" sx={{ mb: 3, maxWidth: 1064 }}>{formError}</Alert>}

      {!initialized ? (
        <Stack spacing={3} sx={{ maxWidth: 860 }}>
          {[1, 2, 3].map(i => <Skeleton key={i} variant="rounded" height={160} sx={{ borderRadius: 3 }} />)}
        </Stack>
      ) : (
        <Box sx={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
          {mode === 'form' && (
            <Box sx={{ width: 180, flexShrink: 0, position: 'sticky', top: 24, display: { xs: 'none', md: 'block' } }}>
              <List dense disablePadding>
                {SECTIONS.map(s => (
                  <ListItemButton key={s.id} selected={activeSection === s.id} onClick={() => scrollTo(s.id)} sx={{ py: 0.75 }}>
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
          )}

          {/* All editable inputs share the monospace look (labels and helper texts stay in Inter). */}
          <Box
            sx={{
              flex: 1, maxWidth: 860, minWidth: 0,
              '& .MuiInputBase-input': { fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem' },
            }}
          >
            {mode === 'form' ? (
              <Stack spacing={3}>
                <Section id="definition" title="Definition">
                  <TextField label="Name" value={form.name} size="small"
                    onChange={e => setForm({ ...form, name: e.target.value })} fullWidth required />
                  <TextField label="Description" value={form.description || ''} size="small"
                    onChange={e => setForm({ ...form, description: e.target.value || undefined })} fullWidth multiline rows={2} />
                </Section>

                <Section id="execution" title="Execution">
                  <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                    <FormControl fullWidth required size="small">
                      <InputLabel>Target Role</InputLabel>
                      <Select value={form.targetRole} label="Target Role"
                        onChange={e => setForm({ ...form, targetRole: e.target.value })}>
                        <MenuItem value="DB">DB</MenuItem>
                        <MenuItem value="APP">APP</MenuItem>
                        <MenuItem value="ADM">ADM</MenuItem>
                        <MenuItem value="BUILDER">BUILDER (ephemeral build container)</MenuItem>
                      </Select>
                    </FormControl>
                    <FormControl fullWidth size="small">
                      <InputLabel>Execution Type</InputLabel>
                      <Select value={form.executionType || 'EXEC'} label="Execution Type"
                        onChange={e => setForm({ ...form, executionType: e.target.value })}>
                        <MenuItem value="EXEC">Container Exec (run command inside container)</MenuItem>
                        <MenuItem value="HOST">Host (Docker API operation on container)</MenuItem>
                        <MenuItem value="BUILDER">Builder (run in ephemeral ant + JDK 8 container)</MenuItem>
                      </Select>
                    </FormControl>
                  </Box>
                  <TextField label="Command" value={form.command} size="small"
                    onChange={e => setForm({ ...form, command: e.target.value })} fullWidth required
                    multiline minRows={8} maxRows={24}
                    InputProps={{ sx: monoInput }}
                    placeholder={form.executionType === 'HOST' ? 'restart | stop | start' : '#!/bin/bash\n…'}
                    helperText={form.executionType === 'HOST' ? 'Supported commands: restart, stop, start'
                      : form.executionType === 'BUILDER' ? 'Runs in the builder with the workspace at $MADE_WORKSPACE; leave the artifact at /out/package.zip and Monohull stages it to ADM:/tmp/made-package/.'
                      : 'Executed via /bin/bash -c inside the target container.'} />
                  <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                    <TextField label="Working Directory" value={form.workingDir || ''} size="small"
                      onChange={e => setForm({ ...form, workingDir: e.target.value || undefined })} fullWidth
                      placeholder="e.g. /opt/IBM/SMP/maximo/deployment" InputProps={{ sx: monoInput }} />
                    {form.executionType !== 'HOST' && (
                      <TextField label="Run as user" value={form.runAsUser || ''} size="small"
                        onChange={e => setForm({ ...form, runAsUser: e.target.value || undefined })} fullWidth
                        placeholder="e.g. maximo"
                        helperText="If set, runs as `su - {user} -c '…'`. Blank = container default (root)." />
                    )}
                  </Box>
                </Section>

                <Section id="behaviour" title="Behaviour">
                  <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                    <TextField label="Timeout (seconds)" type="number" value={form.timeoutSeconds ?? ''} size="small"
                      onChange={e => setForm({ ...form, timeoutSeconds: e.target.value ? Number(e.target.value) : undefined })} fullWidth
                      helperText="Default: 300 seconds" />
                    <TextField label="Allowed Exit Codes" value={form.allowedExitCodes || ''} size="small"
                      onChange={e => setForm({ ...form, allowedExitCodes: e.target.value || undefined })} fullWidth
                      placeholder="e.g. 0,5"
                      helperText="Comma-separated exit codes treated as success. Exit 0 is always allowed." />
                  </Box>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={form.autoRun || false}
                        onChange={e => setForm({ ...form, autoRun: e.target.checked })}
                      />
                    }
                    label="Include in Auto-Build Pipeline"
                  />
                </Section>

                <Section id="scope" title="Scope"
                  description="Where this action is offered: everywhere, for one image config, or for a single environment.">
                  <FormControl size="small" sx={{ maxWidth: { sm: 420 } }} fullWidth>
                    <InputLabel>Scope</InputLabel>
                    <Select
                      value={scopeFromForm(form)}
                      label="Scope"
                      onChange={e => setForm(applyScopeToForm(form, e.target.value as ScopeValue))}
                    >
                      <MenuItem value="global">Global (all environments)</MenuItem>
                      {imageConfigs.length > 0 && (
                        <MenuItem disabled sx={{ opacity: 0.7, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                          Image Configs
                        </MenuItem>
                      )}
                      {imageConfigs.map(ic => (
                        <MenuItem key={`image-${ic.id}`} value={`image:${ic.id}`}>
                          {ic.client} / {ic.project} ({ic.maximoVersion})
                        </MenuItem>
                      ))}
                      {environments.length > 0 && (
                        <MenuItem disabled sx={{ opacity: 0.7, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                          Environments
                        </MenuItem>
                      )}
                      {environments.map(env => (
                        <MenuItem key={`env-${env.id}`} value={`env:${env.id}`}>
                          {env.name}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Section>
              </Stack>
            ) : (
              <TextField
                value={yamlText}
                onChange={e => setYamlText(e.target.value)}
                fullWidth
                multiline
                minRows={24}
                maxRows={48}
                sx={{ '& .MuiInputBase-input': monoInput }}
                placeholder="# Action definition in YAML"
              />
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
              <Button onClick={handleSave} variant="contained" disabled={isSaving || missing.length > 0}>
                {isSaving ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Action'}
              </Button>
            </Box>
          </Box>
        </Box>
      )}
    </>
  )
}
