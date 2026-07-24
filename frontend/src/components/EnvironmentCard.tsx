import { Card, CardContent, CardActions, Typography, Button, Box, Stack } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { EnvironmentResponse, containerBadgeStatus } from '../api/client'
import ContainerStatusBadge from './ContainerStatusBadge'

interface Props {
  env: EnvironmentResponse
  onStop: (id: number) => void
  onStart: (id: number) => void
  onDelete: (id: number) => void
}

export default function EnvironmentCard({ env, onStop, onStart, onDelete }: Props) {
  const navigate = useNavigate()

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flex: 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h6">{env.name}</Typography>
          <ContainerStatusBadge status={env.status} />
        </Box>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {env.maximoVersion} / {env.dbVendor}
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          {env.containers.map(c => (
            <Box key={c.id} sx={{ textAlign: 'center' }}>
              <Typography variant="caption" display="block">{c.role}</Typography>
              <ContainerStatusBadge status={containerBadgeStatus(c)} />
            </Box>
          ))}
        </Stack>
      </CardContent>
      <CardActions>
        <Button size="small" onClick={() => navigate(`/environments/${env.id}`)}>Details</Button>
        {env.status === 'RUNNING' && (
          <Button size="small" color="warning" onClick={() => onStop(env.id)}>Stop</Button>
        )}
        {env.status === 'STOPPED' && (
          <Button size="small" color="success" onClick={() => onStart(env.id)}>Start</Button>
        )}
        <Button size="small" color="error" onClick={() => onDelete(env.id)}>Remove</Button>
      </CardActions>
    </Card>
  )
}
