import { useState } from 'react'
import { Button, Dialog, DialogTitle, DialogContent, DialogActions, Menu, MenuItem, ListItemText, ListItemIcon, Box, useMediaQuery, useTheme } from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrowRounded'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import { useMutation } from '@tanstack/react-query'
import { ActionDefinition, executeAction } from '../api/client'
import { useActionLogStream } from '../hooks/useActionLogStream'
import LogViewer from './LogViewer'
import ContainerStatusBadge from './ContainerStatusBadge'

interface Props {
  envId: number
  containerId: number
  containerRole: string
  actions: ActionDefinition[]
}

export default function ContainerActions({ envId, containerId, containerRole, actions }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const [executionId, setExecutionId] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [actionName, setActionName] = useState('')
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const { lines } = useActionLogStream(executionId)

  const matchingActions = actions.filter(a => a.targetRole === containerRole)

  const executeMutation = useMutation({
    mutationFn: (actionId: string) => executeAction(envId, { actionId, containerId }),
    onSuccess: (data) => {
      setExecutionId(data.executionId)
      setDialogOpen(true)
    },
  })

  const handleExecute = (action: ActionDefinition) => {
    setAnchorEl(null)
    setActionName(action.name)
    setExecutionId(null)
    executeMutation.mutate(action.id)
  }

  const handleClose = () => {
    setDialogOpen(false)
    setExecutionId(null)
  }

  if (matchingActions.length === 0) return null

  const isFinished = lines.some(l => l.includes('[action] Finished'))
  const hasError = lines.some(l => l.includes('[error]'))
  const status = hasError ? 'FAILED' : isFinished ? 'COMPLETED' : 'RUNNING'

  return (
    <>
      <Button
        size="small"
        variant="outlined"
        endIcon={<ArrowDropDownIcon />}
        onClick={e => setAnchorEl(e.currentTarget)}
        disabled={executeMutation.isPending}
        sx={{ mt: 1 }}
      >
        Actions
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        {matchingActions.map(action => (
          <MenuItem key={action.id} onClick={() => handleExecute(action)}>
            <ListItemIcon><PlayArrowIcon fontSize="small" sx={{ color: '#6366f1' }} /></ListItemIcon>
            <ListItemText
              primary={action.name}
              secondary={action.description || undefined}
              primaryTypographyProps={{ fontSize: '0.9rem', fontWeight: 500 }}
              secondaryTypographyProps={{ fontSize: '0.75rem' }}
            />
          </MenuItem>
        ))}
      </Menu>

      <Dialog open={dialogOpen} onClose={handleClose} maxWidth="md" fullWidth fullScreen={fullScreen}>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, fontWeight: 600 }}>
          {actionName}
          {executionId && <ContainerStatusBadge status={status} />}
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <Box sx={{ flex: 1, minHeight: 320, height: '60vh' }}>
            <LogViewer lines={lines} filename={actionName} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleClose} variant="outlined">Close</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
