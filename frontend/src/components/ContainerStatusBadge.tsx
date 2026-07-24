import { Box, Typography } from '@mui/material'

const statusConfig: Record<string, { color: string; label: string }> = {
  RUNNING: { color: '#22c55e', label: 'Running' },
  CREATING: { color: '#f59e0b', label: 'Creating' },
  BUILDING: { color: '#f59e0b', label: 'Building' },
  CONFIGURING: { color: '#6366f1', label: 'Configuring' },
  PENDING: { color: '#64748b', label: 'Pending' },
  STOPPED: { color: '#ef4444', label: 'Stopped' },
  ERROR: { color: '#ef4444', label: 'Error' },
  REMOVED: { color: '#64748b', label: 'Removed' },
  COMPLETED: { color: '#22c55e', label: 'Completed' },
  FAILED: { color: '#ef4444', label: 'Failed' },
  SKIPPED: { color: '#64748b', label: 'Skipped' },
  // PR build lifecycle
  QUEUED: { color: '#64748b', label: 'Queued' },
  CLONING: { color: '#f59e0b', label: 'Cloning' },
  SUCCESS: { color: '#22c55e', label: 'Success' },
  CANCELLED: { color: '#64748b', label: 'Cancelled' },
  SUPERSEDED: { color: '#64748b', label: 'Superseded' },
}

interface Props {
  status: string
}

export default function ContainerStatusBadge({ status }: Props) {
  const cfg = statusConfig[status] || { color: '#64748b', label: status }
  const isActive = status === 'RUNNING' || status === 'BUILDING' || status === 'CREATING'
    || status === 'CONFIGURING' || status === 'CLONING'

  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.75,
        px: 1.25,
        py: 0.4,
        borderRadius: 2,
        bgcolor: `${cfg.color}14`,
        border: `1px solid ${cfg.color}20`,
      }}
    >
      <Box
        sx={{
          width: 7,
          height: 7,
          borderRadius: '50%',
          bgcolor: cfg.color,
          flexShrink: 0,
          ...(isActive && {
            animation: 'statusPulse 2s ease-in-out infinite',
            '@keyframes statusPulse': {
              '0%, 100%': { boxShadow: `0 0 0 0 ${cfg.color}50` },
              '50%': { boxShadow: `0 0 0 5px ${cfg.color}00` },
            },
          }),
        }}
      />
      <Typography
        variant="caption"
        sx={{
          color: cfg.color,
          fontWeight: 600,
          fontSize: '0.7rem',
          letterSpacing: '0.03em',
          textTransform: 'uppercase',
          lineHeight: 1,
        }}
      >
        {cfg.label}
      </Typography>
    </Box>
  )
}
