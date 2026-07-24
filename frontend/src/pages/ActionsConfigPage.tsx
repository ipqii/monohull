import {
  Typography, Box, Button, Card, CardContent, CardActions,
  Grid, Stack, Alert, Chip, Skeleton,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import ContentCopyIcon from '@mui/icons-material/ContentCopyRounded'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getCustomActions, deleteCustomAction,
  getImageConfigs, getEnvironments, CustomAction, downloadYaml,
} from '../api/client'
import { actionToYaml } from '../utils/actionYaml'

export default function ActionsConfigPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data: actions = [], isLoading, error } = useQuery({
    queryKey: ['customActions'],
    queryFn: getCustomActions,
  })

  const { data: imageConfigs = [] } = useQuery({
    queryKey: ['imageConfigs'],
    queryFn: getImageConfigs,
  })

  const { data: environments = [] } = useQuery({
    queryKey: ['environments'],
    queryFn: getEnvironments,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCustomAction,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customActions'] }),
  })

  const builtInActions = actions.filter(a => a.builtIn)
  const customActions = actions.filter(a => !a.builtIn)

  const renderActionCard = (a: CustomAction) => (
    <Grid item xs={12} sm={6} md={4} key={a.id}>
      <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <CardContent sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1, flexWrap: 'wrap' }}>
            <Typography variant="subtitle1" fontWeight={600}>{a.name}</Typography>
            <Chip
              label={a.targetRole}
              size="small"
              sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(99,102,241,0.1)', color: '#818cf8', border: 'none' }}
            />
            {a.executionType === 'HOST' && (
              <Chip label="Host" size="small" sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(245,158,11,0.12)', color: '#fbbf24', border: 'none' }} />
            )}
            {a.executionType === 'BUILDER' && (
              <Chip label="Builder" size="small" sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(34,211,238,0.12)', color: '#22d3ee', border: 'none' }} />
            )}
            {a.builtIn && (
              <Chip label="Built-in" size="small" sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(34,211,238,0.1)', color: '#67e8f9', border: 'none' }} />
            )}
            {a.autoRun && (
              <Chip label="Auto" size="small" sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(34,197,94,0.1)', color: '#4ade80', border: 'none' }} />
            )}
          </Box>
          {a.description && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                mb: 1, fontSize: '0.85rem',
                display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
              }}
            >
              {a.description}
            </Typography>
          )}
          {a.workingDir && (
            <Typography variant="caption" display="block" sx={{ color: '#64748b', fontSize: '0.7rem' }}>
              Dir: {a.workingDir}
            </Typography>
          )}
          <Typography variant="caption" display="block" sx={{ color: '#475569', fontSize: '0.68rem' }}>
            Timeout: {a.timeoutSeconds}s | Key: {a.actionKey}
            {a.imageConfigId ? ` | Config #${a.imageConfigId}` : ''}
            {a.environmentId ? ` | Env #${a.environmentId}` : ''}
            {a.allowedExitCodes ? ` | OK exits: ${a.allowedExitCodes}` : ''}
            {a.runAsUser ? ` | Runs as: ${a.runAsUser}` : ''}
          </Typography>
        </CardContent>
        <CardActions sx={{ px: 2, pb: 1.5, pt: 0, gap: 0.5 }}>
          <Button size="small" variant="outlined" startIcon={<EditIcon sx={{ fontSize: '16px !important' }} />} onClick={() => navigate(`/config/actions/${a.id}/edit`)}>
            Edit
          </Button>
          <Button size="small" variant="outlined" startIcon={<ContentCopyIcon sx={{ fontSize: '16px !important' }} />} onClick={() => navigate('/config/actions/new', { state: { from: a } })}>
            Clone
          </Button>
          <Button
            size="small"
            variant="outlined"
            startIcon={<DownloadIcon sx={{ fontSize: '16px !important' }} />}
            onClick={() => downloadYaml(actionToYaml(a, imageConfigs, environments), `${a.actionKey}.action.yaml`)}
          >
            Export
          </Button>
          {!a.builtIn && (
            <Button
              size="small"
              variant="outlined"
              color="error"
              startIcon={<DeleteIcon sx={{ fontSize: '16px !important' }} />}
              onClick={() => {
                if (window.confirm(`Delete action "${a.name}"?`)) {
                  deleteMutation.mutate(a.id)
                }
              }}
            >
              Delete
            </Button>
          )}
        </CardActions>
      </Card>
    </Grid>
  )

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, flexWrap: 'wrap', gap: 2, mb: 4 }}>
        <Box>
          <Typography variant="h4">Actions</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            Configure commands to run on your containers
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={() => {
              const content = actions.map(a => actionToYaml(a, imageConfigs, environments)).join('---\n')
              downloadYaml(content, 'actions.yaml')
            }}
            disabled={actions.length === 0}
          >
            Export All
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/config/actions/new')}>Add Action</Button>
        </Stack>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>Failed to load actions</Alert>}

      {isLoading && (
        <Grid container spacing={2.5}>
          {[1, 2, 3].map(i => (
            <Grid item xs={12} sm={6} md={4} key={i}>
              <Skeleton variant="rounded" height={180} sx={{ borderRadius: 3 }} />
            </Grid>
          ))}
        </Grid>
      )}

      {builtInActions.length > 0 && (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, mt: 1 }}>
            <Typography variant="subtitle2" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.75rem' }}>
              Built-in Actions
            </Typography>
            <Box sx={{ flex: 1, height: '1px', bgcolor: 'rgba(148,163,184,0.08)' }} />
          </Box>
          <Grid container spacing={2} sx={{ mb: 4 }}>
            {builtInActions.map(renderActionCard)}
          </Grid>
        </>
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, mt: 1 }}>
        <Typography variant="subtitle2" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.75rem' }}>
          Custom Actions
        </Typography>
        <Box sx={{ flex: 1, height: '1px', bgcolor: 'rgba(148,163,184,0.08)' }} />
      </Box>

      {!isLoading && customActions.length === 0 && (
        <Box
          sx={{
            textAlign: 'center', py: 6, borderRadius: 3,
            border: '1px dashed rgba(99, 102, 241, 0.2)',
            background: 'rgba(99, 102, 241, 0.03)',
          }}
        >
          <Typography variant="body2" color="text.secondary">
            No custom actions yet. Click "Add Action" to create one.
          </Typography>
        </Box>
      )}
      <Grid container spacing={2}>
        {customActions.map(renderActionCard)}
      </Grid>

    </>
  )
}
