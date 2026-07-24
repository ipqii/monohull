import { useParams, useNavigate } from 'react-router-dom'
import {
  Typography, Box, Button, Card, CardContent, CardActions,
  Grid, Stack, Alert, Tabs, Tab, TextField, Chip, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, Paper,
  Stepper, Step, StepLabel, StepContent, CircularProgress,
  Collapse, IconButton, Skeleton, FormControl, InputLabel, Select, MenuItem,
  Tooltip, Link, Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material'
import KeyIcon from '@mui/icons-material/KeyRounded'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
import ArrowBackIcon from '@mui/icons-material/ArrowBackRounded'
import OpenInNewIcon from '@mui/icons-material/OpenInNewRounded'
import ContentCopyIcon from '@mui/icons-material/ContentCopyRounded'
import VisibilityIcon from '@mui/icons-material/VisibilityRounded'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOffRounded'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type MouseEvent, type ReactNode } from 'react'
import {
  getEnvironment, stopEnvironment, startEnvironment, deleteEnvironment,
  restartContainer, stopContainer, startContainer,
  getConfig, updateConfig, getLogHistory,
  getActions, getActionHistory, ActionDefinition, ActionExecution,
  getPipelineStatus, startPipeline, PipelineStatus,
  getExecutionLogHistory, LogLine,
  getPipelines, setEnvironmentPipeline,
  setMaximoUserPassword,
  containerBadgeStatus,
} from '../api/client'
import ContainerStatusBadge from '../components/ContainerStatusBadge'
import ContainerActions from '../components/ContainerActions'
import LogViewer from '../components/LogViewer'
import ContainerLogsDialog from '../components/ContainerLogsDialog'
import ContainerExtrasEditor from '../components/ContainerExtrasEditor'
import { ExtraBind, ExtraEnvVar } from '../api/client'
import { useLogStream } from '../hooks/useLogStream'

function ExecutionRow({ exec, expanded, onToggle }: {
  exec: ActionExecution
  expanded: boolean
  onToggle: () => void
}) {
  const { data: logs } = useQuery({
    queryKey: ['executionLogs', exec.executionId],
    queryFn: () => getExecutionLogHistory(exec.executionId),
    enabled: expanded,
  })

  return (
    <>
      <TableRow
        sx={{ cursor: 'pointer', '& > *': { borderBottom: expanded ? 'unset' : undefined } }}
        hover
        onClick={onToggle}
      >
        <TableCell>
          <IconButton size="small" sx={{ color: '#64748b' }}>
            {expanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Typography variant="body2" fontWeight={500}>{exec.actionKey}</Typography>
        </TableCell>
        <TableCell>
          <ContainerStatusBadge status={exec.status} />
        </TableCell>
        <TableCell>
          <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem' }}>
            {exec.exitCode ?? '-'}
          </Typography>
        </TableCell>
        <TableCell>
          <Typography variant="caption" color="text.secondary">
            {exec.startedAt ? new Date(exec.startedAt).toLocaleString() : '-'}
          </Typography>
        </TableCell>
        <TableCell>
          <Typography variant="caption" color="text.secondary">
            {exec.finishedAt ? new Date(exec.finishedAt).toLocaleString() : '-'}
          </Typography>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={6}>
          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <Box sx={{ py: 1.5 }}>
              <Typography variant="caption" fontWeight={600} sx={{ mb: 0.5, display: 'block', color: 'text.secondary' }}>
                Execution Log
              </Typography>
              {!logs || logs.length === 0 ? (
                <Typography variant="body2" color="text.secondary">No log output captured.</Typography>
              ) : (
                <Box
                  sx={{
                    maxHeight: 300,
                    overflow: 'auto',
                    bgcolor: '#020617',
                    color: '#94a3b8',
                    p: 2,
                    borderRadius: 2,
                    fontFamily: '"JetBrains Mono", monospace',
                    fontSize: '0.78rem',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                    lineHeight: 1.6,
                    border: '1px solid rgba(34, 211, 238, 0.06)',
                  }}
                >
                  {logs.map((l, i) => (
                    <div key={i}>{l.line}</div>
                  ))}
                </Box>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  )
}

function CopyChipValue({ value, mono = true }: { value: string; mono?: boolean }) {
  const [copied, setCopied] = useState(false)
  const handleCopy = async (e: MouseEvent) => {
    e.stopPropagation()
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch { /* ignore */ }
  }
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, minWidth: 0 }}>
      <Typography
        variant="body2"
        sx={{
          fontFamily: mono ? '"JetBrains Mono", monospace' : undefined,
          fontSize: '0.82rem',
          color: '#e2e8f0',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          minWidth: 0,
        }}
        title={value}
      >
        {value}
      </Typography>
      <Tooltip title={copied ? 'Copied' : 'Copy'} placement="top">
        <IconButton size="small" onClick={handleCopy} sx={{ color: copied ? '#22c55e' : '#64748b', p: 0.25 }}>
          <ContentCopyIcon sx={{ fontSize: '0.9rem' }} />
        </IconButton>
      </Tooltip>
    </Box>
  )
}

function AccessRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minHeight: 28 }}>
      <Typography
        variant="caption"
        sx={{
          color: 'text.secondary',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          fontSize: '0.65rem',
          fontWeight: 600,
          minWidth: 90,
          flexShrink: 0,
        }}
      >
        {label}
      </Typography>
      <Box sx={{ flex: 1, minWidth: 0 }}>{children}</Box>
    </Box>
  )
}

function AccessCard({
  envId, dbVendor, dbName, publicUrl, appHttpPort, appHttpsPort, dbPort, dbPassword,
}: {
  envId: number
  dbVendor: string
  dbName: string
  publicUrl: string | null
  appHttpPort: number | null
  appHttpsPort: number | null
  dbPort: number | null
  dbPassword: string | null
}) {
  const [showPassword, setShowPassword] = useState(false)
  // Use the same hostname the user used to reach Monohull — so the LAN links work
  // whether Monohull is local (localhost) or on a remote dockerserver (its hostname/IP).
  const accessHost = window.location.hostname
  const httpUrl = appHttpPort ? `http://${accessHost}:${appHttpPort}/maximo` : null
  const httpsUrl = appHttpsPort ? `https://${accessHost}:${appHttpsPort}/maximo` : null
  // Prefer the public URL (served over 443 via Traefik + the wildcard route) when
  // the deployment advertises one; the host:port URLs are LAN/VPN-only.
  const primaryUrl = publicUrl || httpUrl || httpsUrl
  const isOracle = dbVendor.toUpperCase() === 'ORACLE'
  const jdbcUrl = dbPort
    ? (isOracle
      ? `jdbc:oracle:thin:@//${accessHost}:${dbPort}/${dbName}`
      : `jdbc:db2://${accessHost}:${dbPort}/${dbName}`)
    : null
  const password = dbPassword || 'maximo'

  return (
    <Card sx={{ mb: 2.5, borderLeft: '3px solid #22d3ee' }}>
      <CardContent sx={{ pb: '16px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
          <Typography
            variant="subtitle2"
            sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.7rem' }}
          >
            Access
          </Typography>
          {primaryUrl && (
            <Button
              size="small"
              variant="contained"
              endIcon={<OpenInNewIcon sx={{ fontSize: '0.95rem' }} />}
              href={primaryUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Open Maximo
            </Button>
          )}
        </Box>

        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Stack spacing={0.75}>
              <Typography variant="caption" sx={{ color: '#22d3ee', fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Maximo UI
              </Typography>
              {primaryUrl && (
                <AccessRow label="URL">
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, minWidth: 0 }}>
                    <Link
                      href={primaryUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#818cf8', textDecoration: 'none', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0, '&:hover': { textDecoration: 'underline' } }}
                    >
                      {primaryUrl}
                    </Link>
                    <Tooltip title="Copy" placement="top">
                      <IconButton size="small" onClick={() => navigator.clipboard.writeText(primaryUrl)} sx={{ color: '#64748b', p: 0.25 }}>
                        <ContentCopyIcon sx={{ fontSize: '0.9rem' }} />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </AccessRow>
              )}
              {/* When a public URL is advertised, the host:port URL is the LAN/VPN fallback. */}
              {publicUrl && (httpUrl || httpsUrl) && (
                <AccessRow label="Internal (LAN)">
                  <CopyChipValue value={(httpUrl || httpsUrl)!} />
                </AccessRow>
              )}
              {!publicUrl && httpsUrl && (
                <AccessRow label="HTTPS">
                  <CopyChipValue value={httpsUrl} />
                </AccessRow>
              )}
              <AccessRow label="Login">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                    maxadmin / maxadmin
                  </Typography>
                  <ChangeMaximoPasswordButton envId={envId} />
                </Box>
              </AccessRow>
            </Stack>
          </Grid>

          <Grid item xs={12} md={6}>
            <Stack spacing={0.75}>
              <Typography variant="caption" sx={{ color: '#22d3ee', fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Database ({isOracle ? 'Oracle' : 'DB2'})
              </Typography>
              {jdbcUrl && (
                <AccessRow label="JDBC URL">
                  <CopyChipValue value={jdbcUrl} />
                </AccessRow>
              )}
              <AccessRow label="Host : Port">
                <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                  {accessHost}{dbPort ? `:${dbPort}` : ''} &nbsp;·&nbsp; db: {dbName}
                </Typography>
              </AccessRow>
              <AccessRow label="User">
                <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                  maximo
                </Typography>
              </AccessRow>
              <AccessRow label="Password">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                    {showPassword ? password : '••••••••'}
                  </Typography>
                  <Tooltip title={showPassword ? 'Hide' : 'Show'} placement="top">
                    <IconButton size="small" onClick={() => setShowPassword(s => !s)} sx={{ color: '#64748b', p: 0.25 }}>
                      {showPassword ? <VisibilityOffIcon sx={{ fontSize: '0.95rem' }} /> : <VisibilityIcon sx={{ fontSize: '0.95rem' }} />}
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Copy" placement="top">
                    <IconButton size="small" onClick={() => navigator.clipboard.writeText(password)} sx={{ color: '#64748b', p: 0.25 }}>
                      <ContentCopyIcon sx={{ fontSize: '0.9rem' }} />
                    </IconButton>
                  </Tooltip>
                </Box>
              </AccessRow>
            </Stack>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  )
}

function ChangeMaximoPasswordButton({ envId }: { envId: number }) {
  const [open, setOpen] = useState(false)
  const [loginId, setLoginId] = useState('maxadmin')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  const mutation = useMutation({
    mutationFn: () => setMaximoUserPassword(envId, loginId.trim(), password),
  })

  const close = () => {
    setOpen(false)
    setPassword('')
    setConfirm('')
    setLoginId('maxadmin')
    mutation.reset()
  }

  const mismatch = confirm.length > 0 && password !== confirm
  const canSubmit = loginId.trim().length > 0 && password.length > 0 && !mismatch && !mutation.isPending

  return (
    <>
      <Tooltip title="Change Maximo password" placement="top">
        <IconButton size="small" onClick={() => setOpen(true)} sx={{ color: '#64748b', p: 0.25, '&:hover': { color: '#22d3ee' } }}>
          <KeyIcon sx={{ fontSize: '0.95rem' }} />
        </IconButton>
      </Tooltip>
      <Dialog open={open} onClose={close} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontSize: '1.05rem' }}>Change Maximo password</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Re-encrypts the password and writes it to MAXUSER on this environment's ADM container.
            The environment must be running.
          </Typography>
          <Stack spacing={2}>
            <TextField
              label="Login ID" size="small" fullWidth value={loginId}
              onChange={e => setLoginId(e.target.value)}
              autoComplete="off"
            />
            <TextField
              label="New password" type="password" size="small" fullWidth value={password}
              onChange={e => setPassword(e.target.value)}
              autoComplete="new-password"
            />
            <TextField
              label="Confirm password" type="password" size="small" fullWidth value={confirm}
              onChange={e => setConfirm(e.target.value)}
              error={mismatch}
              helperText={mismatch ? 'Passwords do not match' : ' '}
              autoComplete="new-password"
            />
            {mutation.isError && (
              <Alert severity="error">
                {(mutation.error as { response?: { data?: { error?: string } } })?.response?.data?.error
                  ?? 'Failed to change password.'}
              </Alert>
            )}
            {mutation.isSuccess && (
              mutation.data.success
                ? <Alert severity="success">Password updated for {loginId.trim()}.</Alert>
                : <Alert severity="error">
                    Update failed.
                    <Box component="pre" sx={{ mt: 1, fontSize: '0.7rem', whiteSpace: 'pre-wrap', maxHeight: 160, overflow: 'auto' }}>
                      {mutation.data.output}
                    </Box>
                  </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close} color="inherit">Close</Button>
          <Button variant="contained" disabled={!canSubmit} onClick={() => mutation.mutate()}>
            {mutation.isPending ? 'Setting…' : 'Set password'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function parseHostPort(mapping: string, containerPort: string): number | null {
  if (!mapping) return null
  for (const part of mapping.split(',')) {
    const [host, cont] = part.split(':')
    if (cont?.trim() === containerPort) {
      const n = Number(host)
      return Number.isFinite(n) ? n : null
    }
  }
  return null
}

function AddonsCard({ mockHostPort, smtpUiHostPort, smtpHostPort }: {
  mockHostPort: number | null
  smtpUiHostPort: number | null
  smtpHostPort: number | null
}) {
  const accessHost = window.location.hostname
  const mockUrl = mockHostPort ? `http://${accessHost}:${mockHostPort}/__mock/` : null
  const mailpitUrl = smtpUiHostPort ? `http://${accessHost}:${smtpUiHostPort}` : null
  return (
    <Card sx={{ mb: 2.5, borderLeft: '3px solid #818cf8' }}>
      <CardContent sx={{ pb: '16px !important' }}>
        <Typography
          variant="subtitle2"
          sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.7rem', mb: 1.5 }}
        >
          Test Addons
        </Typography>
        <Grid container spacing={2}>
          {mockHostPort != null && (
            <Grid item xs={12} md={6}>
              <Stack spacing={0.75}>
                <Typography variant="caption" sx={{ color: '#818cf8', fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Mock Receiver
                </Typography>
                <AccessRow label="From Maximo">
                  <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                    http://mock:8085
                  </Typography>
                </AccessRow>
                {mockUrl && (
                  <AccessRow label="Mock UI">
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, minWidth: 0 }}>
                      <Link
                        href={mockUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#818cf8', textDecoration: 'none', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0, '&:hover': { textDecoration: 'underline' } }}
                      >
                        {mockUrl}
                      </Link>
                    </Box>
                  </AccessRow>
                )}
              </Stack>
            </Grid>
          )}
          {(smtpHostPort != null || smtpUiHostPort != null) && (
            <Grid item xs={12} md={6}>
              <Stack spacing={0.75}>
                <Typography variant="caption" sx={{ color: '#818cf8', fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  SMTP (Mailpit)
                </Typography>
                {smtpHostPort != null && (
                  <AccessRow label="From Maximo">
                    <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                      smtp:1025
                    </Typography>
                  </AccessRow>
                )}
                {smtpHostPort != null && (
                  <AccessRow label="SMTP (host)">
                    <Typography variant="body2" sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#e2e8f0' }}>
                      {accessHost}:{smtpHostPort}
                    </Typography>
                  </AccessRow>
                )}
                {mailpitUrl && (
                  <AccessRow label="Inbox UI">
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, minWidth: 0 }}>
                      <Link
                        href={mailpitUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82rem', color: '#818cf8', textDecoration: 'none', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0, '&:hover': { textDecoration: 'underline' } }}
                      >
                        {mailpitUrl}
                      </Link>
                    </Box>
                  </AccessRow>
                )}
              </Stack>
            </Grid>
          )}
        </Grid>
      </CardContent>
    </Card>
  )
}

export default function EnvironmentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const envId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState(0)
  const [expandedExecId, setExpandedExecId] = useState<string | null>(null)
  const [logsContainer, setLogsContainer] = useState<{ id: number; name: string } | null>(null)

  const { data: env, isLoading, error } = useQuery({
    queryKey: ['environment', envId],
    queryFn: () => getEnvironment(envId),
    refetchInterval: 5000,
  })

  const { lines: liveLines } = useLogStream(
    env ? envId : null,
    env?.buildId ?? null
  )

  // Paginated log history. The /logs/history endpoint can return millions of rows for long
  // builds, which used to OOM the browser tab. We fetch the tail first and let the user pull
  // older pages on demand.
  const LOG_PAGE_SIZE = 2000
  const [logHistory, setLogHistory] = useState<{
    total: number
    lines: LogLine[]
    oldestOffset: number
  } | null>(null)
  const [loadingOlder, setLoadingOlder] = useState(false)

  useEffect(() => {
    if (tab !== 2) return
    let cancelled = false
    setLogHistory(null)
    getLogHistory(envId, { limit: LOG_PAGE_SIZE }).then(page => {
      if (cancelled) return
      setLogHistory({ total: page.total, lines: page.lines, oldestOffset: page.offset })
    }).catch(() => {
      if (cancelled) return
      setLogHistory({ total: 0, lines: [], oldestOffset: 0 })
    })
    return () => { cancelled = true }
  }, [tab, envId])

  const loadOlderLogs = async () => {
    if (!logHistory || logHistory.oldestOffset === 0 || loadingOlder) return
    setLoadingOlder(true)
    try {
      const newOffset = Math.max(0, logHistory.oldestOffset - LOG_PAGE_SIZE)
      const page = await getLogHistory(envId, { offset: newOffset, limit: LOG_PAGE_SIZE })
      setLogHistory(prev => prev ? {
        total: page.total,
        lines: [...page.lines, ...prev.lines],
        oldestOffset: page.offset,
      } : null)
    } finally {
      setLoadingOlder(false)
    }
  }

  const { data: config } = useQuery({
    queryKey: ['config', envId],
    queryFn: () => getConfig(envId),
    enabled: tab === 0 || tab === 3,
  })

  const { data: actions = [] } = useQuery({
    queryKey: ['actions', envId],
    queryFn: () => getActions(envId),
    enabled: tab === 0,
  })

  const { data: actionHistory = [] } = useQuery({
    queryKey: ['actionHistory', envId],
    queryFn: () => getActionHistory(envId),
    enabled: tab === 4,
    refetchInterval: 5000,
  })

  const isPipelineActive = env?.status === 'RUNNING' || env?.status === 'CONFIGURING' || env?.status === 'BUILDING'

  const { data: pipelineStatus } = useQuery({
    queryKey: ['pipelineStatus', envId],
    queryFn: () => getPipelineStatus(envId),
    enabled: tab === 1,
    refetchInterval: isPipelineActive ? 3000 : false,
  })

  const stopEnvMutation = useMutation({
    mutationFn: () => stopEnvironment(envId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environment', envId] }),
  })

  const startEnvMutation = useMutation({
    mutationFn: () => startEnvironment(envId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environment', envId] }),
  })

  const deleteEnvMutation = useMutation({
    mutationFn: () => deleteEnvironment(envId),
    onSuccess: () => navigate('/'),
  })

  const restartMutation = useMutation({
    mutationFn: restartContainer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environment', envId] }),
  })

  const stopContainerMutation = useMutation({
    mutationFn: stopContainer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environment', envId] }),
  })

  const startContainerMutation = useMutation({
    mutationFn: startContainer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environment', envId] }),
  })

  const configMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => updateConfig(envId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['config', envId] }),
  })

  const { data: allPipelines = [] } = useQuery({
    queryKey: ['pipelines'],
    queryFn: getPipelines,
    enabled: tab === 3,
  })

  const envPipelines = allPipelines.filter(
    p => p.environmentId == null || p.environmentId === envId,
  )

  const setPipelineMutation = useMutation({
    mutationFn: (pipelineId: number | null) => setEnvironmentPipeline(envId, pipelineId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['config', envId] }),
  })

  const pipelineMutation = useMutation({
    mutationFn: () => startPipeline(envId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipelineStatus', envId] })
      queryClient.invalidateQueries({ queryKey: ['environment', envId] })
    },
  })

  if (isLoading) {
    return (
      <Box>
        <Skeleton variant="text" width={200} height={40} />
        <Skeleton variant="text" width={300} height={24} sx={{ mb: 3 }} />
        <Grid container spacing={2}>
          {[1, 2, 3].map(i => (
            <Grid item xs={12} md={4} key={i}>
              <Skeleton variant="rounded" height={180} sx={{ borderRadius: 3 }} />
            </Grid>
          ))}
        </Grid>
      </Box>
    )
  }

  if (error || !env) return <Alert severity="error">Environment not found</Alert>

  return (
    <>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Button
          onClick={() => navigate('/')}
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 1.5, color: 'text.secondary', fontWeight: 500, '&:hover': { color: 'text.primary' } }}
        >
          Back to Dashboard
        </Button>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 2 }}>
          <Box sx={{ minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5, flexWrap: 'wrap' }}>
              <Typography variant="h4" sx={{ wordBreak: 'break-word' }}>{env.name}</Typography>
              <ContainerStatusBadge status={env.status} />
            </Box>
            <Stack direction="row" spacing={1.5} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
              <Typography variant="body2" color="text.secondary">
                Maximo {env.maximoVersion}
              </Typography>
              <Typography variant="body2" color="text.secondary">|</Typography>
              <Typography variant="body2" color="text.secondary">
                {env.dbVendor}
              </Typography>
              <Typography variant="body2" color="text.secondary">|</Typography>
              <Typography variant="body2" sx={{ color: '#64748b', fontFamily: '"JetBrains Mono", monospace', fontSize: '0.8rem' }}>
                {env.buildId}
              </Typography>
            </Stack>
          </Box>
          <Stack direction="row" spacing={1}>
            {env.status === 'RUNNING' && (
              <Button variant="outlined" color="warning" size="small" onClick={() => stopEnvMutation.mutate()}>
                Stop All
              </Button>
            )}
            {env.status === 'STOPPED' && (
              <Button variant="outlined" color="success" size="small" onClick={() => startEnvMutation.mutate()}>
                Start All
              </Button>
            )}
            <Button
              variant="outlined"
              color="error"
              size="small"
              onClick={() => {
                if (window.confirm('Remove this environment?')) deleteEnvMutation.mutate()
              }}
            >
              Remove
            </Button>
          </Stack>
        </Box>
      </Box>

      {/* Tabs */}
      <Box sx={{ borderBottom: '1px solid rgba(148, 163, 184, 0.08)', mb: 3 }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
        >
          <Tab label="Containers" />
          <Tab label="Pipeline" />
          <Tab label="Logs" />
          <Tab label="Configuration" />
          <Tab label="History" />
        </Tabs>
      </Box>

      {/* Containers Tab */}
      {tab === 0 && (
        <>
          {env.status === 'RUNNING' && (
            <AccessCard
              envId={envId}
              dbVendor={env.dbVendor}
              dbName={env.dbName}
              publicUrl={env.publicUrl}
              appHttpPort={config?.appHttpPort ?? null}
              appHttpsPort={config?.appHttpsPort ?? null}
              dbPort={config?.dbPort ?? null}
              dbPassword={config?.dbPassword ?? null}
            />
          )}
          {(() => {
            const mockContainer = env.containers.find(c => c.role === 'MOCK')
            const smtpContainer = env.containers.find(c => c.role === 'SMTP')
            if (!mockContainer && !smtpContainer) return null
            const mockHostPort = mockContainer ? parseHostPort(mockContainer.ports ?? '', '8085') : null
            const smtpHostPort = smtpContainer ? parseHostPort(smtpContainer.ports ?? '', '1025') : null
            const smtpUiHostPort = smtpContainer ? parseHostPort(smtpContainer.ports ?? '', '8025') : null
            return (
              <AddonsCard
                mockHostPort={mockHostPort}
                smtpHostPort={smtpHostPort}
                smtpUiHostPort={smtpUiHostPort}
              />
            )
          })()}
          <Grid container spacing={2}>
            {env.containers.map(c => {
            const running = c.liveState?.running
            return (
              <Grid item xs={12} sm={6} md={4} key={c.id}>
                <Card
                  sx={{
                    borderTop: `3px solid ${running ? '#22c55e' : '#334155'}`,
                  }}
                >
                  <CardContent>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                      <Typography variant="h6" fontSize="1rem">{c.role}</Typography>
                      <ContainerStatusBadge status={containerBadgeStatus(c)} />
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.75, minWidth: 0 }}>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: '"JetBrains Mono", monospace',
                          fontSize: '0.78rem',
                          color: '#e2e8f0',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          flex: 1,
                          minWidth: 0,
                        }}
                        title={c.containerName}
                      >
                        {c.containerName}
                      </Typography>
                      <Tooltip title="Copy container name" placement="top">
                        <IconButton
                          size="small"
                          onClick={() => navigator.clipboard.writeText(c.containerName)}
                          sx={{ color: '#64748b', p: 0.25, '&:hover': { color: '#22d3ee' } }}
                        >
                          <ContentCopyIcon sx={{ fontSize: '0.85rem' }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1, minWidth: 0 }}>
                      <Typography
                        variant="caption"
                        sx={{
                          fontFamily: '"JetBrains Mono", monospace',
                          fontSize: '0.7rem',
                          color: '#64748b',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          flex: 1,
                          minWidth: 0,
                        }}
                        title={c.image}
                      >
                        {c.image}
                      </Typography>
                      <Tooltip title="Copy image" placement="top">
                        <IconButton
                          size="small"
                          onClick={() => navigator.clipboard.writeText(c.image)}
                          sx={{ color: '#64748b', p: 0.25, '&:hover': { color: '#22d3ee' } }}
                        >
                          <ContentCopyIcon sx={{ fontSize: '0.85rem' }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                    {c.liveState && (
                      <Box sx={{ mb: 1 }}>
                        <Typography variant="caption" display="block" color="text.secondary">
                          State: {c.liveState.state}
                        </Typography>
                        {c.liveState.startedAt && (
                          <Typography variant="caption" display="block" color="text.secondary">
                            Started: {new Date(c.liveState.startedAt).toLocaleString()}
                          </Typography>
                        )}
                      </Box>
                    )}
                    {c.ports && (
                      <Chip
                        label={`Ports: ${c.ports}`}
                        size="small"
                        variant="outlined"
                        sx={{ fontSize: '0.7rem', height: 22, borderColor: 'rgba(148,163,184,0.12)', mb: 0.5 }}
                      />
                    )}
                    <ContainerActions
                      envId={envId}
                      containerId={c.id}
                      containerRole={c.role}
                      actions={actions}
                    />
                  </CardContent>
                  <CardActions sx={{ px: 2, pb: 1.5, pt: 0, gap: 0.5, flexWrap: 'wrap' }}>
                    <Button size="small" variant="outlined" onClick={() => restartMutation.mutate(c.id)}>
                      Restart
                    </Button>
                    {running ? (
                      <Button size="small" variant="outlined" color="warning" onClick={() => stopContainerMutation.mutate(c.id)}>
                        Stop
                      </Button>
                    ) : (
                      <Button size="small" variant="outlined" color="success" onClick={() => startContainerMutation.mutate(c.id)}>
                        Start
                      </Button>
                    )}
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => setLogsContainer({ id: c.id, name: c.containerName })}
                    >
                      Logs
                    </Button>
                  </CardActions>
                </Card>
              </Grid>
            )
          })}
          </Grid>
        </>
      )}

      {/* Pipeline Tab */}
      {tab === 1 && (
        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Typography variant="h6">Action Pipeline</Typography>
              {pipelineStatus && pipelineStatus.pipelineRunId && (
                <ContainerStatusBadge status={pipelineStatus.status} />
              )}
            </Box>
            {(env.status === 'RUNNING' || env.status === 'STOPPED' || env.status === 'ERROR') && (
              <Button
                variant="contained"
                size="small"
                onClick={() => pipelineMutation.mutate()}
                disabled={pipelineMutation.isPending}
              >
                {pipelineMutation.isPending ? 'Starting...' : 'Re-run Pipeline'}
              </Button>
            )}
          </Box>

          {!pipelineStatus || !pipelineStatus.pipelineRunId ? (
            <Box
              sx={{
                textAlign: 'center', py: 6, borderRadius: 3,
                border: '1px dashed rgba(99, 102, 241, 0.2)',
                background: 'rgba(99, 102, 241, 0.03)',
              }}
            >
              <Typography variant="body2" color="text.secondary">
                No pipeline has been executed yet for this environment.
              </Typography>
            </Box>
          ) : (
            <Paper sx={{ p: 3 }}>
              <Stepper orientation="vertical" activeStep={-1}>
                {pipelineStatus.steps.map((step) => (
                  <Step key={step.executionId} expanded active completed={false}>
                    <StepLabel
                      icon={
                        step.status === 'RUNNING' ? <CircularProgress size={22} sx={{ color: '#f59e0b' }} /> :
                        step.status === 'COMPLETED' ? (
                          <Box sx={{ width: 24, height: 24, borderRadius: '50%', bgcolor: '#22c55e20', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#22c55e' }} />
                          </Box>
                        ) :
                        step.status === 'FAILED' ? (
                          <Box sx={{ width: 24, height: 24, borderRadius: '50%', bgcolor: '#ef444420', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ef4444' }} />
                          </Box>
                        ) :
                        <Box sx={{ width: 24, height: 24, borderRadius: '50%', bgcolor: 'rgba(148,163,184,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <Typography variant="caption" sx={{ fontSize: '0.65rem', fontWeight: 600, color: '#64748b' }}>
                            {step.order + 1}
                          </Typography>
                        </Box>
                      }
                    >
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Typography variant="subtitle2" fontWeight={600}>{step.actionName}</Typography>
                        <Chip
                          label={step.targetRole}
                          size="small"
                          variant="outlined"
                          sx={{ fontSize: '0.65rem', height: 22, borderColor: 'rgba(148,163,184,0.15)' }}
                        />
                        <ContainerStatusBadge status={step.status} />
                        {step.exitCode !== null && step.exitCode !== undefined && (
                          <Typography variant="caption" sx={{ color: '#64748b', fontFamily: '"JetBrains Mono", monospace' }}>
                            exit: {step.exitCode}
                          </Typography>
                        )}
                      </Box>
                    </StepLabel>
                    <StepContent>
                      <Stack spacing={0.25}>
                        {step.startedAt && step.status !== 'PENDING' && (
                          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
                            Started: {new Date(step.startedAt).toLocaleString()}
                          </Typography>
                        )}
                        {step.finishedAt && step.status !== 'SKIPPED' && (
                          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
                            Finished: {new Date(step.finishedAt).toLocaleString()}
                          </Typography>
                        )}
                      </Stack>
                    </StepContent>
                  </Step>
                ))}
              </Stepper>
            </Paper>
          )}
        </Box>
      )}

      {/* Logs Tab */}
      {tab === 2 && (
        <Box sx={{ height: { xs: 'calc(100vh - 360px)', md: 'calc(100vh - 280px)' }, minHeight: 320, display: 'flex', flexDirection: 'column' }}>
          {liveLines.length === 0 && logHistory && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                Showing {logHistory.lines.length.toLocaleString()} of {logHistory.total.toLocaleString()} lines
                {logHistory.oldestOffset > 0 && ` (offset ${logHistory.oldestOffset.toLocaleString()})`}
              </Typography>
              {logHistory.oldestOffset > 0 && (
                <Button size="small" variant="outlined" disabled={loadingOlder} onClick={loadOlderLogs}>
                  {loadingOlder ? 'Loading…' : `Load older ${LOG_PAGE_SIZE} lines`}
                </Button>
              )}
            </Box>
          )}
          <Box sx={{ flex: 1, minHeight: 0 }}>
            <LogViewer
              lines={liveLines.length > 0 ? liveLines : (logHistory?.lines.map(l => l.line) ?? [])}
              filename={env?.name || 'build'}
            />
          </Box>
        </Box>
      )}

      {/* Configuration Tab */}
      {tab === 3 && config && (
        <Card sx={{ maxWidth: 900 }}>
          <CardContent>
            <Typography variant="h6" fontSize="1rem" sx={{ mb: 2.5 }}>Environment Configuration</Typography>
            <Stack spacing={2.5}>
              <TextField
                label="Host Volume Path"
                defaultValue={config.hostVolumePath || ''}
                onBlur={e => configMutation.mutate({ ...config, hostVolumePath: e.target.value || null })}
                helperText="Base path on host. Subfolders config/ and logs/ are created per environment."
                fullWidth size="small"
              />
              <TextField
                label="DB Volume Name"
                defaultValue={config.dbVolumeName || ''}
                onBlur={e => configMutation.mutate({ ...config, dbVolumeName: e.target.value || null })}
                fullWidth size="small"
              />
              <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
                <TextField
                  label="HTTP Port"
                  type="number"
                  defaultValue={config.appHttpPort || ''}
                  onBlur={e => configMutation.mutate({ ...config, appHttpPort: Number(e.target.value) || null })}
                  size="small"
                  fullWidth
                />
                <TextField
                  label="HTTPS Port"
                  type="number"
                  defaultValue={config.appHttpsPort || ''}
                  onBlur={e => configMutation.mutate({ ...config, appHttpsPort: Number(e.target.value) || null })}
                  size="small"
                  fullWidth
                />
                <TextField
                  label="DB Port"
                  type="number"
                  defaultValue={config.dbPort || ''}
                  onBlur={e => configMutation.mutate({ ...config, dbPort: Number(e.target.value) || null })}
                  size="small"
                  fullWidth
                />
              </Box>
              <TextField
                label="Database Password"
                type="password"
                defaultValue={config.dbPassword || ''}
                onBlur={e => configMutation.mutate({ ...config, dbPassword: e.target.value || null })}
                helperText="Passed to the DB container as MAXIMO_DB_PASSWORD. Used by in-container scripts (e.g. database restore) that need to authenticate to Maximo."
                fullWidth size="small"
                autoComplete="new-password"
              />

              <FormControl fullWidth size="small">
                <InputLabel>Pipeline (override)</InputLabel>
                <Select
                  value={config.pipelineDefinitionId == null ? '' : String(config.pipelineDefinitionId)}
                  label="Pipeline (override)"
                  onChange={e => {
                    const v = e.target.value
                    setPipelineMutation.mutate(v === '' ? null : Number(v))
                  }}
                >
                  <MenuItem value=""><em>Use image-config default</em></MenuItem>
                  {envPipelines.map(p => (
                    <MenuItem key={p.id} value={String(p.id)}>
                      {p.name}{p.environmentId != null ? '' : ' (global)'}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <ContainerExtrasEditor
                title="DB container extras"
                envVars={config.dbExtraEnv ?? []}
                binds={config.dbExtraBinds ?? []}
                onChange={({ envVars, binds }: { envVars: ExtraEnvVar[]; binds: ExtraBind[] }) =>
                  configMutation.mutate({ ...config, dbExtraEnv: envVars, dbExtraBinds: binds })}
              />
              <ContainerExtrasEditor
                title="APP container extras"
                envVars={config.appExtraEnv ?? []}
                binds={config.appExtraBinds ?? []}
                onChange={({ envVars, binds }: { envVars: ExtraEnvVar[]; binds: ExtraBind[] }) =>
                  configMutation.mutate({ ...config, appExtraEnv: envVars, appExtraBinds: binds })}
              />
              <ContainerExtrasEditor
                title="ADM container extras"
                envVars={config.admExtraEnv ?? []}
                binds={config.admExtraBinds ?? []}
                onChange={({ envVars, binds }: { envVars: ExtraEnvVar[]; binds: ExtraBind[] }) =>
                  configMutation.mutate({ ...config, admExtraEnv: envVars, admExtraBinds: binds })}
              />
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Actions History Tab */}
      {tab === 4 && (
        <Box>
          {actionHistory.length === 0 ? (
            <Box
              sx={{
                textAlign: 'center', py: 6, borderRadius: 3,
                border: '1px dashed rgba(99, 102, 241, 0.2)',
                background: 'rgba(99, 102, 241, 0.03)',
              }}
            >
              <Typography variant="body2" color="text.secondary">No action executions yet.</Typography>
            </Box>
          ) : (
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={40} />
                    <TableCell>Action</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Exit Code</TableCell>
                    <TableCell>Started</TableCell>
                    <TableCell>Finished</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {actionHistory.map(exec => (
                    <ExecutionRow
                      key={exec.executionId}
                      exec={exec}
                      expanded={expandedExecId === exec.executionId}
                      onToggle={() => setExpandedExecId(
                        expandedExecId === exec.executionId ? null : exec.executionId
                      )}
                    />
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}

      <ContainerLogsDialog
        open={logsContainer !== null}
        onClose={() => setLogsContainer(null)}
        containerId={logsContainer?.id ?? null}
        containerName={logsContainer?.name ?? ''}
      />
    </>
  )
}
