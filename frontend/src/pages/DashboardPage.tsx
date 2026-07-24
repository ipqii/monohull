import { useState } from 'react'
import {
  Typography, Button, Box, Alert, Grid, Card, CardContent, CardActions,
  Stack, IconButton, Tooltip, Skeleton, Chip,
} from '@mui/material'
import StopIcon from '@mui/icons-material/StopRounded'
import PlayArrowIcon from '@mui/icons-material/PlayArrowRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import OpenInNewIcon from '@mui/icons-material/ArrowForwardRounded'
import AddIcon from '@mui/icons-material/AddRounded'
import RocketLaunchIcon from '@mui/icons-material/RocketLaunchRounded'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getEnvironments, stopEnvironment, startEnvironment, deleteEnvironment } from '../api/client'
import ContainerStatusBadge from '../components/ContainerStatusBadge'
import BuildForm from '../components/BuildForm'
import ProfileLauncher from '../components/ProfileLauncher'

const statusBorderColor: Record<string, string> = {
  RUNNING: '#22c55e',
  BUILDING: '#f59e0b',
  CREATING: '#f59e0b',
  CONFIGURING: '#6366f1',
  ERROR: '#ef4444',
  STOPPED: '#ef4444',
}

export default function DashboardPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [formOpen, setFormOpen] = useState(false)
  const [launcherOpen, setLauncherOpen] = useState(false)

  const { data: environments = [], isLoading, error } = useQuery({
    queryKey: ['environments'],
    queryFn: getEnvironments,
    refetchInterval: 5000,
  })

  const stopMutation = useMutation({
    mutationFn: stopEnvironment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environments'] }),
  })

  const startMutation = useMutation({
    mutationFn: startEnvironment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environments'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteEnvironment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environments'] }),
  })

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, flexWrap: 'wrap', gap: 2, mb: 4 }}>
        <Box>
          <Typography variant="h4">Dashboard</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            Manage your development environments
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button variant="outlined" startIcon={<RocketLaunchIcon />} onClick={() => setLauncherOpen(true)}>
            Launch Profile
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setFormOpen(true)}>
            New Build
          </Button>
        </Stack>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>Failed to load environments</Alert>}

      {isLoading && (
        <Grid container spacing={2.5}>
          {[1, 2, 3].map(i => (
            <Grid item xs={12} sm={6} lg={4} key={i}>
              <Card>
                <CardContent>
                  <Skeleton variant="text" width="60%" height={28} />
                  <Skeleton variant="text" width="40%" height={20} sx={{ mb: 2 }} />
                  <Stack direction="row" spacing={1}>
                    {[1, 2, 3].map(j => (
                      <Skeleton key={j} variant="rounded" width="33%" height={52} sx={{ borderRadius: 2 }} />
                    ))}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {!isLoading && environments.length === 0 && (
        <Box
          sx={{
            textAlign: 'center',
            py: 8,
            px: 3,
            borderRadius: 4,
            border: '1px dashed rgba(99, 102, 241, 0.2)',
            background: 'rgba(99, 102, 241, 0.03)',
          }}
        >
          <Typography variant="h6" sx={{ color: 'text.secondary', mb: 1 }}>No environments yet</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
            Launch a shared profile to go from zero to a building environment in one click,
            or create one manually with New Build.
          </Typography>
          <Stack direction="row" spacing={1.5} justifyContent="center">
            <Button variant="contained" startIcon={<RocketLaunchIcon />} onClick={() => setLauncherOpen(true)}>
              Launch Profile
            </Button>
            <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setFormOpen(true)}>
              New Build
            </Button>
          </Stack>
        </Box>
      )}

      <Grid container spacing={2.5}>
        {environments.map(env => (
          <Grid item xs={12} sm={6} lg={4} key={env.id}>
            <Card
              sx={{
                cursor: 'pointer',
                borderLeft: `3px solid ${statusBorderColor[env.status] || '#334155'}`,
                '&:hover': { transform: 'translateY(-3px)' },
              }}
              onClick={() => navigate(`/environments/${env.id}`)}
            >
              <CardContent sx={{ pb: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={600} noWrap>{env.name}</Typography>
                  </Box>
                  <ContainerStatusBadge status={env.status} />
                </Box>

                <Stack direction="row" spacing={0.75} sx={{ mb: 2 }}>
                  <Chip
                    label={`v${env.maximoVersion}`}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.7rem', height: 24, borderColor: 'rgba(148,163,184,0.15)' }}
                  />
                  <Chip
                    label={env.dbVendor}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.7rem', height: 24, borderColor: 'rgba(148,163,184,0.15)' }}
                  />
                </Stack>

                {/* Container mini-cards */}
                <Box sx={{ display: 'flex', gap: 1 }}>
                  {env.containers.map(c => {
                    const running = c.liveState?.running
                    return (
                      <Box
                        key={c.id}
                        sx={{
                          flex: 1,
                          bgcolor: 'rgba(15, 23, 42, 0.5)',
                          borderRadius: 2,
                          p: 1,
                          textAlign: 'center',
                          border: '1px solid',
                          borderColor: running ? 'rgba(34, 197, 94, 0.15)' : 'rgba(148,163,184,0.06)',
                          transition: 'border-color 0.2s',
                        }}
                      >
                        <Typography
                          variant="caption"
                          sx={{ fontWeight: 600, fontSize: '0.65rem', color: '#94a3b8', display: 'block', mb: 0.25 }}
                        >
                          {c.role}
                        </Typography>
                        <Box
                          sx={{
                            width: 6,
                            height: 6,
                            borderRadius: '50%',
                            bgcolor: running ? '#22c55e' : '#64748b',
                            mx: 'auto',
                            ...(running && {
                              boxShadow: '0 0 8px rgba(34, 197, 94, 0.4)',
                            }),
                          }}
                        />
                      </Box>
                    )
                  })}
                </Box>
              </CardContent>

              <CardActions sx={{ px: 2, pb: 1.5, pt: 0, justifyContent: 'flex-end' }} onClick={e => e.stopPropagation()}>
                <Tooltip title="Details" placement="top">
                  <IconButton size="small" onClick={() => navigate(`/environments/${env.id}`)} sx={{ color: '#64748b' }}>
                    <OpenInNewIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                {env.status === 'RUNNING' && (
                  <Tooltip title="Stop" placement="top">
                    <IconButton size="small" onClick={() => stopMutation.mutate(env.id)} sx={{ color: '#f59e0b' }}>
                      <StopIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {env.status === 'STOPPED' && (
                  <Tooltip title="Start" placement="top">
                    <IconButton size="small" onClick={() => startMutation.mutate(env.id)} sx={{ color: '#22c55e' }}>
                      <PlayArrowIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                <Tooltip title="Remove" placement="top">
                  <IconButton
                    size="small"
                    onClick={() => {
                      if (window.confirm(`Remove environment "${env.name}"? This will stop and remove all containers.`)) {
                        deleteMutation.mutate(env.id)
                      }
                    }}
                    sx={{ color: '#64748b', '&:hover': { color: '#ef4444' } }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <BuildForm open={formOpen} onClose={() => setFormOpen(false)} />
      <ProfileLauncher open={launcherOpen} onClose={() => setLauncherOpen(false)} />
    </>
  )
}
