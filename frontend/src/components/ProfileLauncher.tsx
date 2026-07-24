import { useRef, useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  Stack, Box, Alert, Typography, Chip, Divider, Tooltip,
  FormControlLabel, Switch, CircularProgress,
  useMediaQuery, useTheme,
} from '@mui/material'
import RocketLaunchIcon from '@mui/icons-material/RocketLaunchRounded'
import UploadFileIcon from '@mui/icons-material/UploadFileOutlined'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getImageConfigs, launchProfile, launchProfileBundle, ImageConfig,
} from '../api/client'

interface Props {
  open: boolean
  onClose: () => void
}

function serverError(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { error?: string; message?: string } } }
  return e?.response?.data?.error ?? e?.response?.data?.message ?? fallback
}

/**
 * One-click profile launcher (MXF-20). A profile is an image config with its saved
 * launch defaults; launching provisions an environment (name generated server-side)
 * and kicks off the build. A shared .bundle.yaml can be uploaded and launched in the
 * same click — the template is imported first when this instance doesn't have it.
 */
export default function ProfileLauncher({ open, onClose }: Props) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [overwrite, setOverwrite] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: profiles = [], isLoading } = useQuery({
    queryKey: ['imageConfigs'],
    queryFn: getImageConfigs,
    enabled: open,
  })

  const onLaunched = (envId: number) => {
    queryClient.invalidateQueries({ queryKey: ['environments'] })
    queryClient.invalidateQueries({ queryKey: ['imageConfigs'] })
    handleClose()
    navigate(`/environments/${envId}`)
  }

  const launchMutation = useMutation({
    mutationFn: launchProfile,
    onSuccess: r => onLaunched(r.environment.id),
    onError: err => setError(serverError(err, 'Launch failed. Check server logs.')),
  })

  const bundleMutation = useMutation({
    mutationFn: ({ yaml, ow }: { yaml: string; ow: boolean }) => launchProfileBundle(yaml, ow),
    onSuccess: r => onLaunched(r.environment.id),
    onError: err => setError(serverError(err, 'Import/launch failed. Check the bundle YAML.')),
  })

  const busy = launchMutation.isPending || bundleMutation.isPending

  const handleClose = () => {
    onClose()
    setError(null)
    setOverwrite(false)
  }

  const handleFile = async (file: File | undefined) => {
    if (!file) return
    setError(null)
    bundleMutation.mutate({ yaml: await file.text(), ow: overwrite })
  }

  const launchChips = (p: ImageConfig) => {
    const chips: string[] = []
    if (p.launchStaticPorts) chips.push('static ports')
    if (p.launchIncludeMock) chips.push('mock')
    if (p.launchIncludeSmtp) chips.push('smtp')
    return chips
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : handleClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 600 }}>Launch a profile</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}

        {isLoading && <CircularProgress size={24} sx={{ display: 'block', mx: 'auto', my: 3 }} />}

        {!isLoading && profiles.length === 0 && (
          <Alert severity="info" sx={{ mb: 1 }}>
            No profiles on this instance yet. Import a shared profile below, or define an
            image configuration under Environments.
          </Alert>
        )}

        <Stack spacing={1.25} sx={{ mt: 0.5 }}>
          {profiles.map(p => {
            const launchable = p.pipelineId != null
            const chips = launchChips(p)
            return (
              <Box
                key={p.id}
                sx={{
                  display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5,
                  borderRadius: 2.5,
                  border: '1px solid rgba(148,163,184,0.12)',
                  background: 'rgba(99, 102, 241, 0.03)',
                }}
              >
                <Box sx={{ minWidth: 0, flex: 1 }}>
                  <Typography variant="subtitle2" noWrap>
                    {p.client} / {p.project} — Maximo {p.maximoVersion}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }} noWrap>
                    {p.launchDescription || `${p.dbVendor}${p.pipelineName ? ` · ${p.pipelineName}` : ''}`}
                  </Typography>
                  {chips.length > 0 && (
                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                      {chips.map(c => (
                        <Chip key={c} label={c} size="small" variant="outlined"
                          sx={{ fontSize: '0.65rem', height: 20, borderColor: 'rgba(148,163,184,0.2)' }} />
                      ))}
                    </Stack>
                  )}
                </Box>
                <Tooltip title={launchable ? '' : 'No pipeline linked — a launched environment would never build. Link one on the Environments page.'}>
                  <span>
                    <Button
                      variant="contained"
                      size="small"
                      startIcon={<RocketLaunchIcon />}
                      disabled={!launchable || busy}
                      onClick={() => { setError(null); launchMutation.mutate(p.id) }}
                    >
                      {launchMutation.isPending && launchMutation.variables === p.id ? 'Launching…' : 'Launch'}
                    </Button>
                  </span>
                </Tooltip>
              </Box>
            )
          })}
        </Stack>

        <Divider sx={{ my: 2.5 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>or launch a shared profile</Typography>
        </Divider>

        <Stack spacing={1}>
          <Button
            variant="outlined"
            startIcon={bundleMutation.isPending ? <CircularProgress size={16} /> : <UploadFileIcon />}
            disabled={busy}
            onClick={() => fileInputRef.current?.click()}
          >
            {bundleMutation.isPending ? 'Importing & launching…' : 'Import .bundle.yaml and launch'}
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".yaml,.yml"
            hidden
            onChange={e => { handleFile(e.target.files?.[0]); e.target.value = '' }}
          />
          <FormControlLabel
            control={<Switch size="small" checked={overwrite} onChange={e => setOverwrite(e.target.checked)} />}
            label={
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                Overwrite this instance's copy when the profile already exists (otherwise the existing copy is launched as-is)
              </Typography>
            }
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={handleClose} disabled={busy}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}
