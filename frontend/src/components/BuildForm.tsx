import { useState, useEffect, useCallback } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  TextField, FormControl, InputLabel, Select, MenuItem,
  FormControlLabel, Switch, Stack, Box, Alert, ListSubheader,
  ToggleButtonGroup, ToggleButton, Tooltip, Typography,
  useMediaQuery, useTheme,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getImageConfigs, getNextSequence, createEnvironment, CreateEnvironmentRequest, ImageConfig } from '../api/client'
import yaml from 'js-yaml'

interface Props {
  open: boolean
  onClose: () => void
}

type DialogMode = 'form' | 'yaml'

function envFormToYaml(form: CreateEnvironmentRequest, imageConfigs: ImageConfig[]): string {
  const obj: Record<string, unknown> = {
    name: form.name || '',
    imageConfigId: form.imageConfigId || 0,
  }
  if (form.imageConfigId) {
    const ic = imageConfigs.find(c => c.id === form.imageConfigId)
    if (ic) obj['# imageConfig'] = `${ic.client} / ${ic.project} - Maximo ${ic.maximoVersion}`
  }
  obj.staticPorts = form.staticPorts
  if (form.staticPorts) {
    if (form.appHttpPort != null) obj.appHttpPort = form.appHttpPort
    if (form.appHttpsPort != null) obj.appHttpsPort = form.appHttpsPort
    if (form.dbPort != null) obj.dbPort = form.dbPort
  }
  if (form.includeMock) {
    obj.includeMock = true
    if (form.staticPorts && form.mockHostPort != null) obj.mockHostPort = form.mockHostPort
  }
  if (form.includeSmtp) {
    obj.includeSmtp = true
    if (form.staticPorts) {
      if (form.smtpHostPort != null) obj.smtpHostPort = form.smtpHostPort
      if (form.smtpUiHostPort != null) obj.smtpUiHostPort = form.smtpUiHostPort
    }
  }
  return yaml.dump(obj, { lineWidth: -1 })
}

function yamlToEnvForm(text: string): CreateEnvironmentRequest {
  const obj = yaml.load(text) as Record<string, unknown>
  if (!obj || typeof obj !== 'object') throw new Error('Invalid YAML')
  return {
    name: String(obj.name ?? ''),
    imageConfigId: obj.imageConfigId != null ? Number(obj.imageConfigId) : 0,
    staticPorts: obj.staticPorts === true,
    appHttpPort: obj.appHttpPort != null ? Number(obj.appHttpPort) : undefined,
    appHttpsPort: obj.appHttpsPort != null ? Number(obj.appHttpsPort) : undefined,
    dbPort: obj.dbPort != null ? Number(obj.dbPort) : undefined,
    includeMock: obj.includeMock === true,
    mockHostPort: obj.mockHostPort != null ? Number(obj.mockHostPort) : undefined,
    includeSmtp: obj.includeSmtp === true,
    smtpHostPort: obj.smtpHostPort != null ? Number(obj.smtpHostPort) : undefined,
    smtpUiHostPort: obj.smtpUiHostPort != null ? Number(obj.smtpUiHostPort) : undefined,
  }
}

function downloadYaml(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/yaml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

const emptyForm: CreateEnvironmentRequest = { name: '', imageConfigId: 0, staticPorts: false }

export default function BuildForm({ open, onClose }: Props) {
  const queryClient = useQueryClient()
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const { data: imageConfigs = [] } = useQuery({ queryKey: ['imageConfigs'], queryFn: getImageConfigs })

  const [form, setForm] = useState<CreateEnvironmentRequest>(emptyForm)
  const [dialogMode, setDialogMode] = useState<DialogMode>('form')
  const [yamlText, setYamlText] = useState('')
  const [yamlError, setYamlError] = useState<string | null>(null)

  const selectedConfig = imageConfigs.find(c => c.id === form.imageConfigId)

  useEffect(() => {
    if (!selectedConfig) return
    let cancelled = false
    getNextSequence(selectedConfig.client, selectedConfig.project).then(seq => {
      if (cancelled) return
      const name = `monohull-${selectedConfig.client}-${selectedConfig.project}-${seq}`
        .toLowerCase().replace(/[^a-z0-9-]/g, '-')
      setForm(prev => ({ ...prev, name }))
    })
    return () => { cancelled = true }
  }, [selectedConfig?.id])

  // Keep YAML in sync when form changes (only if in yaml mode would cause stale text,
  // so we update yamlText whenever the form changes while in form mode so Export always works)
  useEffect(() => {
    if (dialogMode === 'form') {
      setYamlText(envFormToYaml(form, imageConfigs))
    }
  }, [form, imageConfigs, dialogMode])

  const handleModeSwitch = useCallback((_: unknown, newMode: DialogMode | null) => {
    if (!newMode) return
    setYamlError(null)
    if (newMode === 'yaml') {
      setYamlText(envFormToYaml(form, imageConfigs))
    } else {
      try {
        setForm(yamlToEnvForm(yamlText))
      } catch {
        setYamlError('Invalid YAML. Fix errors before switching to form view.')
        return
      }
    }
    setDialogMode(newMode)
  }, [form, yamlText, imageConfigs])

  const handleExport = () => {
    const content = dialogMode === 'yaml' ? yamlText : envFormToYaml(form, imageConfigs)
    const filename = (form.name.trim() || 'environment').toLowerCase().replace(/[^a-z0-9-]/g, '-')
    downloadYaml(content, `${filename}.environment.yaml`)
  }

  const mutation = useMutation({
    mutationFn: createEnvironment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] })
      handleClose()
    },
  })

  const handleClose = () => {
    onClose()
    setForm(emptyForm)
    setDialogMode('form')
    setYamlText('')
    setYamlError(null)
  }

  const handleSubmit = () => {
    let submittedForm = form
    if (dialogMode === 'yaml') {
      try {
        submittedForm = yamlToEnvForm(yamlText)
        setYamlError(null)
      } catch (e: unknown) {
        setYamlError(e instanceof Error ? e.message : 'Invalid YAML')
        return
      }
    }
    if (submittedForm.name && submittedForm.imageConfigId) {
      mutation.mutate(submittedForm)
    }
  }

  const byClient = imageConfigs.reduce((acc, c) => {
    (acc[c.client] = acc[c.client] || []).push(c)
    return acc
  }, {} as Record<string, typeof imageConfigs>)

  const toggleButtonSx = {
    '& .MuiToggleButton-root': {
      textTransform: 'none',
      fontWeight: 500,
      fontSize: '0.8rem',
      px: 1.5,
      py: 0.25,
      borderColor: 'rgba(99,102,241,0.2)',
      '&.Mui-selected': { bgcolor: 'rgba(99,102,241,0.12)', color: '#818cf8' },
    },
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1, flexWrap: 'wrap', fontWeight: 600 }}>
        New Environment
        <ToggleButtonGroup
          value={dialogMode}
          exclusive
          onChange={handleModeSwitch}
          size="small"
          sx={toggleButtonSx}
        >
          <ToggleButton value="form">Form</ToggleButton>
          <ToggleButton value="yaml">YAML</ToggleButton>
        </ToggleButtonGroup>
      </DialogTitle>
      <DialogContent>
        {yamlError && <Alert severity="error" sx={{ mb: 2 }}>{yamlError}</Alert>}
        {mutation.isError && !yamlError && (
          <Alert severity="error" sx={{ mb: 2 }}>Failed to create environment. Check server logs.</Alert>
        )}

        {dialogMode === 'form' ? (
          <Stack spacing={2} sx={{ mt: 1 }}>
            {imageConfigs.length === 0 && (
              <Alert severity="warning">No image configurations defined. Go to Environments to add one first.</Alert>
            )}
            <FormControl fullWidth required size="small">
              <InputLabel>Image Configuration</InputLabel>
              <Select
                value={form.imageConfigId || ''}
                label="Image Configuration"
                onChange={e => setForm({ ...form, imageConfigId: Number(e.target.value) })}
              >
                {Object.entries(byClient).flatMap(([client, configs]) => [
                  <ListSubheader key={`header-${client}`}>{client}</ListSubheader>,
                  ...configs.map(c => (
                    <MenuItem key={c.id} value={c.id}>
                      {c.project} — Maximo {c.maximoVersion}
                    </MenuItem>
                  ))
                ])}
              </Select>
            </FormControl>
            <TextField
              label="Environment Name"
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              fullWidth
              required
              size="small"
              helperText="Auto-generated from client/project. Edit if needed."
            />
            <FormControlLabel
              control={
                <Switch
                  checked={form.staticPorts}
                  onChange={e => setForm({ ...form, staticPorts: e.target.checked })}
                />
              }
              label="Use static ports"
            />
            {form.staticPorts && selectedConfig && (() => {
              const missing: string[] = []
              if (selectedConfig.appHttpPort == null) missing.push('HTTP')
              if (selectedConfig.appHttpsPort == null) missing.push('HTTPS')
              if (selectedConfig.dbPort == null) missing.push('DB')
              if (form.includeMock && selectedConfig.mockHostPort == null) missing.push('Mock')
              if (form.includeSmtp && selectedConfig.smtpHostPort == null) missing.push('SMTP')
              if (form.includeSmtp && selectedConfig.smtpUiHostPort == null) missing.push('Mailpit UI')
              if (missing.length > 0) {
                return (
                  <Alert severity="warning" sx={{ '& .MuiAlert-message': { fontSize: '0.8rem' } }}>
                    Image config <strong>{selectedConfig.client} / {selectedConfig.project}</strong> has no value for: {missing.join(', ')}. Set them on the Environments page or untoggle Static Ports.
                  </Alert>
                )
              }
              const rows: string[] = [
                `HTTP: ${selectedConfig.appHttpPort}  ·  HTTPS: ${selectedConfig.appHttpsPort}  ·  DB: ${selectedConfig.dbPort}`,
              ]
              if (form.includeMock) rows.push(`Mock: ${selectedConfig.mockHostPort}`)
              if (form.includeSmtp) rows.push(`SMTP: ${selectedConfig.smtpHostPort}  ·  Mailpit UI: ${selectedConfig.smtpUiHostPort}`)
              return (
                <Box sx={{ p: 1.25, borderRadius: 2, border: '1px solid rgba(99,102,241,0.15)', background: 'rgba(99,102,241,0.04)' }}>
                  <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.65rem', fontWeight: 600 }}>
                    Static ports (from image config)
                  </Typography>
                  {rows.map((row, i) => (
                    <Typography key={i} variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.78rem', mt: 0.25 }}>
                      {row}
                    </Typography>
                  ))}
                </Box>
              )
            })()}
            <FormControlLabel
              control={
                <Switch
                  checked={form.includeMock ?? false}
                  onChange={e => setForm({ ...form, includeMock: e.target.checked })}
                />
              }
              label="Include mock receiver (capture outbound HTTP integrations)"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={form.includeSmtp ?? false}
                  onChange={e => setForm({ ...form, includeSmtp: e.target.checked })}
                />
              }
              label="Include SMTP server (Mailpit) for capturing outbound email"
            />
          </Stack>
        ) : (
          <TextField
            value={yamlText}
            onChange={e => { setYamlText(e.target.value); setYamlError(null) }}
            fullWidth
            multiline
            minRows={12}
            maxRows={24}
            sx={{
              mt: 1,
              '& .MuiInputBase-input': {
                fontFamily: '"JetBrains Mono", monospace',
                fontSize: '0.82rem',
                lineHeight: 1.6,
              },
            }}
            placeholder={'name: monohull-client-project-1\nimageConfigId: 1\nstaticPorts: false\nincludeMock: false\nincludeSmtp: false'}
          />
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        <Tooltip title="Download as YAML">
          <Button startIcon={<DownloadIcon />} onClick={handleExport} size="small">
            Export
          </Button>
        </Tooltip>
        <Box sx={{ flex: 1 }} />
        <Button onClick={handleClose}>Cancel</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={mutation.isPending || (!form.imageConfigId && dialogMode === 'form')}
        >
          {mutation.isPending ? 'Creating...' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
