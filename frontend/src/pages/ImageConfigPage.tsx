import { useState } from 'react'
import {
  Typography, Box, Button, Card, CardContent, CardActions,
  Grid, Stack, Dialog, DialogTitle, DialogContent, DialogActions, Alert,
  Chip, Skeleton, FormControlLabel, Switch, useMediaQuery, useTheme,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import UploadIcon from '@mui/icons-material/FileUploadOutlined'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getImageConfigs, deleteImageConfig, downloadYaml,
  exportImageConfigBundle, importImageConfigBundle, BundleImportResult, BundleConflictResponse,
} from '../api/client'
import { AxiosError } from 'axios'

export default function ImageConfigPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))

  const { data: configs = [], isLoading, error } = useQuery({
    queryKey: ['imageConfigs'],
    queryFn: getImageConfigs,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteImageConfig,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['imageConfigs'] }),
  })

  // -- Bundle export/import (image config + linked pipeline + the pipeline's custom actions) --
  const [importOpen, setImportOpen] = useState(false)
  const [importFile, setImportFile] = useState<File | null>(null)
  const [importOverwrite, setImportOverwrite] = useState(false)
  const [importBusy, setImportBusy] = useState(false)
  const [importResult, setImportResult] = useState<BundleImportResult | null>(null)
  const [importError, setImportError] = useState<{ message: string; conflicts: string[] } | null>(null)

  const handleExportBundle = async (id: number) => {
    try {
      const { filename, yaml } = await exportImageConfigBundle(id)
      downloadYaml(yaml, filename)
    } catch (e) {
      const ax = e as AxiosError<{ error?: string }>
      window.alert('Export failed: ' + (ax.response?.data?.error ?? ax.message))
    }
  }

  const openImport = () => {
    setImportFile(null)
    setImportOverwrite(false)
    setImportResult(null)
    setImportError(null)
    setImportOpen(true)
  }

  const closeImport = () => {
    setImportOpen(false)
  }

  const handleImportSubmit = async () => {
    if (!importFile) return
    setImportBusy(true)
    setImportError(null)
    setImportResult(null)
    try {
      const yaml = await importFile.text()
      const result = await importImageConfigBundle(yaml, importOverwrite)
      setImportResult(result)
      queryClient.invalidateQueries({ queryKey: ['imageConfigs'] })
      queryClient.invalidateQueries({ queryKey: ['pipelines'] })
      queryClient.invalidateQueries({ queryKey: ['customActions'] })
    } catch (e) {
      const ax = e as AxiosError<BundleConflictResponse | { error?: string }>
      if (ax.response?.status === 409) {
        const body = ax.response.data as BundleConflictResponse
        setImportError({ message: body.error, conflicts: body.conflicts ?? [] })
      } else {
        setImportError({
          message: (ax.response?.data as { error?: string })?.error ?? ax.message,
          conflicts: [],
        })
      }
    } finally {
      setImportBusy(false)
    }
  }

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, flexWrap: 'wrap', gap: 2, mb: 4 }}>
        <Box>
          <Typography variant="h4">Environments</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            Configure image templates for your environments
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Button variant="outlined" startIcon={<UploadIcon />} onClick={openImport}>Import Bundle</Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/config/environments/new')}>New Environment</Button>
        </Stack>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>Failed to load image configs</Alert>}

      {isLoading && (
        <Grid container spacing={2.5}>
          {[1, 2, 3].map(i => (
            <Grid item xs={12} sm={6} md={4} key={i}>
              <Skeleton variant="rounded" height={200} sx={{ borderRadius: 3 }} />
            </Grid>
          ))}
        </Grid>
      )}

      {!isLoading && configs.length === 0 && (
        <Box
          sx={{
            textAlign: 'center', py: 8, px: 3, borderRadius: 4,
            border: '1px dashed rgba(99, 102, 241, 0.2)',
            background: 'rgba(99, 102, 241, 0.03)',
          }}
        >
          <Typography variant="h6" sx={{ color: 'text.secondary', mb: 1 }}>No environments configured</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
            Add an environment to start creating development instances.
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/config/environments/new')}>New Environment</Button>
        </Box>
      )}

      <Grid container spacing={2.5}>
        {configs.map(c => (
          <Grid item xs={12} sm={6} md={4} key={c.id}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 0.25 }}>
                  {c.client} / {c.project}
                </Typography>
                <Stack direction="row" spacing={0.75} sx={{ mb: 2 }}>
                  <Chip
                    label={`Maximo ${c.maximoVersion}`}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.7rem', height: 22, borderColor: 'rgba(148,163,184,0.15)' }}
                  />
                  <Chip
                    label={c.dbVendor}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.7rem', height: 22, borderColor: 'rgba(148,163,184,0.15)' }}
                  />
                </Stack>

                {/* Image tags */}
                <Stack spacing={0.5} sx={{ mb: 1.5 }}>
                  {[
                    { label: 'APP', value: c.appImage },
                    { label: 'DB', value: c.dbImage },
                    { label: 'ADM', value: c.admImage },
                  ].map(img => (
                    <Box key={img.label} sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75 }}>
                      <Typography
                        variant="caption"
                        sx={{
                          fontWeight: 600, color: '#6366f1', fontSize: '0.6rem', minWidth: 28,
                          textTransform: 'uppercase', letterSpacing: '0.05em',
                        }}
                      >
                        {img.label}
                      </Typography>
                      <Typography
                        variant="caption"
                        sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.68rem', color: '#64748b', wordBreak: 'break-all' }}
                      >
                        {img.value}
                      </Typography>
                    </Box>
                  ))}
                </Stack>

                {(c.hostVolumePath || c.dbVolumeName) && (
                  <Typography variant="caption" display="block" sx={{ color: '#475569', fontSize: '0.7rem' }}>
                    {c.hostVolumePath ? `Volume: ${c.hostVolumePath}` : ''}{c.dbVolumeName ? ` | DB Vol: ${c.dbVolumeName}` : ''}
                  </Typography>
                )}
                {c.workspacePath && (
                  <Typography variant="caption" display="block" sx={{ fontFamily: '"JetBrains Mono", monospace', color: '#475569', fontSize: '0.68rem' }}>
                    Workspace: {c.workspacePath}
                  </Typography>
                )}
                {c.pipelineName && (
                  <Chip
                    label={`Pipeline: ${c.pipelineName}`}
                    size="small"
                    sx={{ mt: 1, fontSize: '0.7rem', height: 22, bgcolor: 'rgba(99,102,241,0.1)', color: '#818cf8', border: 'none' }}
                  />
                )}
              </CardContent>
              <CardActions sx={{ px: 2, pb: 1.5, pt: 0, gap: 0.5, flexWrap: 'wrap' }}>
                <Button size="small" variant="outlined" startIcon={<EditIcon sx={{ fontSize: '16px !important' }} />} onClick={() => navigate(`/config/environments/${c.id}/edit`)}>
                  Edit
                </Button>
                <Button size="small" variant="outlined" startIcon={<DownloadIcon sx={{ fontSize: '16px !important' }} />} onClick={() => handleExportBundle(c.id)}>
                  Export
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  color="error"
                  startIcon={<DeleteIcon sx={{ fontSize: '16px !important' }} />}
                  onClick={() => {
                    if (window.confirm(`Delete image config "${c.client} / ${c.project} / ${c.maximoVersion}"?`)) {
                      deleteMutation.mutate(c.id)
                    }
                  }}
                >
                  Delete
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={importOpen} onClose={closeImport} maxWidth="sm" fullWidth fullScreen={fullScreen}>
        <DialogTitle sx={{ fontWeight: 600 }}>Import Environment Bundle</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Pick a <code>.bundle.yaml</code> exported from another Monohull instance. The bundle's
              image config, linked pipeline, and the pipeline's custom actions are imported in one
              transaction. Built-in actions (from <code>application.yml</code>) resolve on this
              Monohull by <code>actionKey</code>.
            </Typography>
            <Button variant="outlined" component="label" startIcon={<UploadIcon />}>
              {importFile ? importFile.name : 'Choose YAML file'}
              <input
                type="file"
                hidden
                accept=".yaml,.yml,application/x-yaml,application/yaml,text/yaml"
                onChange={e => {
                  const f = e.target.files?.[0]
                  setImportFile(f ?? null)
                  setImportResult(null)
                  setImportError(null)
                }}
              />
            </Button>
            <FormControlLabel
              control={<Switch checked={importOverwrite} onChange={e => setImportOverwrite(e.target.checked)} />}
              label="Overwrite existing rows (image config, pipeline, custom actions)"
            />
            {importError && (
              <Alert severity={importError.conflicts.length > 0 ? 'warning' : 'error'}>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>{importError.message}</Typography>
                {importError.conflicts.length > 0 && (
                  <Box component="ul" sx={{ mt: 1, mb: 0, pl: 2.5 }}>
                    {importError.conflicts.map((c, i) => <li key={i}><Typography variant="body2">{c}</Typography></li>)}
                  </Box>
                )}
              </Alert>
            )}
            {importResult && (
              <Alert severity="success">
                <Typography variant="body2" sx={{ fontWeight: 500 }}>Imported successfully.</Typography>
                <Typography variant="caption" display="block">Image config: {importResult.imageConfig.toLowerCase()} (id {importResult.imageConfigId})</Typography>
                {importResult.pipeline !== 'NONE' && (
                  <Typography variant="caption" display="block">Pipeline: {importResult.pipeline.toLowerCase()} (id {importResult.pipelineId})</Typography>
                )}
                {importResult.createdActionKeys.length > 0 && (
                  <Typography variant="caption" display="block">Created actions: {importResult.createdActionKeys.join(', ')}</Typography>
                )}
                {importResult.updatedActionKeys.length > 0 && (
                  <Typography variant="caption" display="block">Updated actions: {importResult.updatedActionKeys.join(', ')}</Typography>
                )}
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={closeImport}>{importResult ? 'Close' : 'Cancel'}</Button>
          <Button
            onClick={handleImportSubmit}
            variant="contained"
            disabled={!importFile || importBusy || importResult !== null}
          >
            {importBusy ? 'Importing…' : 'Import'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
