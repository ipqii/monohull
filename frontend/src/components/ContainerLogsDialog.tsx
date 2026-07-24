import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Stack, TextField, IconButton, Tooltip, useMediaQuery, useTheme } from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getContainerLogs } from '../api/client'
import LogViewer from './LogViewer'

interface Props {
  open: boolean
  onClose: () => void
  containerId: number | null
  containerName: string
}

export default function ContainerLogsDialog({ open, onClose, containerId, containerName }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const [tail, setTail] = useState(500)
  const [tailInput, setTailInput] = useState('500')

  const { data: lines = [], isFetching, refetch } = useQuery({
    queryKey: ['containerLogs', containerId, tail],
    queryFn: () => getContainerLogs(containerId!, tail),
    enabled: open && containerId !== null,
    refetchOnWindowFocus: false,
  })

  const commitTail = () => {
    const parsed = Math.max(1, Math.min(10000, Number(tailInput) || tail))
    setTailInput(String(parsed))
    if (parsed === tail) {
      refetch()
    } else {
      setTail(parsed)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="lg"
      fullWidth
      fullScreen={fullScreen}
      PaperProps={{ sx: { height: fullScreen ? '100vh' : '90vh', display: 'flex', flexDirection: 'column' } }}
    >
      <DialogTitle sx={{ fontWeight: 600 }}>
        Container Logs — {containerName}
      </DialogTitle>
      <DialogContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
        <Stack
          direction="row"
          spacing={1.5}
          alignItems="center"
          sx={{ mb: 1.5, mt: 2, flexShrink: 0 }}
        >
          <TextField
            label="Tail lines"
            type="number"
            size="small"
            value={tailInput}
            onChange={e => setTailInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); commitTail() } }}
            sx={{ width: 160 }}
            InputLabelProps={{ shrink: true }}
            inputProps={{ min: 1, max: 10000 }}
          />
          <Tooltip title="Refresh">
            <span>
              <IconButton onClick={commitTail} disabled={isFetching} size="small">
                <RefreshIcon />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <LogViewer lines={lines} filename={containerName} />
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} variant="outlined">Close</Button>
      </DialogActions>
    </Dialog>
  )
}
