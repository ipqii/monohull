import { useState } from 'react'
import {
  Typography, Box, Button, Card, CardContent, CardActions, Grid, TextField, Stack,
  Dialog, DialogTitle, DialogContent, DialogActions, Alert, Chip, Skeleton,
  FormControl, InputLabel, Select, MenuItem, Switch, FormControlLabel,
  IconButton, Tooltip,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import ContentCopyIcon from '@mui/icons-material/ContentCopyRounded'
import VisibilityIcon from '@mui/icons-material/VisibilityRounded'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOffRounded'
import HistoryIcon from '@mui/icons-material/HistoryRounded'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getRepositories, createRepository, updateRepository, deleteRepository,
  getImageConfigs,
  ConnectedRepository, ConnectedRepositoryRequest, RepoProvider, RepoBuildMode, RepoAuthMethod,
} from '../api/client'
import PrBuildsDialog from '../components/PrBuildsDialog'

const PROVIDERS: { value: RepoProvider; label: string }[] = [
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'BITBUCKET', label: 'Bitbucket' },
  { value: 'GITLAB', label: 'GitLab' },
]

const AUTH_METHODS: { value: RepoAuthMethod; label: string }[] = [
  { value: 'HTTPS', label: 'HTTPS (token)' },
  { value: 'SSH', label: 'SSH (deploy key)' },
]

const BUILD_MODES: { value: RepoBuildMode; label: string; hint: string }[] = [
  { value: 'BUILD_ONLY', label: 'Build check only', hint: 'Clone + build + report pass/fail. No running environment kept.' },
  { value: 'BUILD_AND_ENV', label: 'Build + ephemeral env', hint: 'Also deploy a full Maximo env for testing, removed on PR close.' },
]

const emptyForm: ConnectedRepositoryRequest = {
  name: '', provider: 'BITBUCKET', authMethod: 'HTTPS', repoUrl: '', repoFullName: '',
  defaultBranch: 'main', buildMode: 'BUILD_ONLY', imageConfigId: 0,
  cloneUsername: '', cloneToken: '', sshPrivateKey: '', sshPassphrase: '',
  maxConcurrent: 2, enabled: true,
}

/** Resolve the webhook URL to an absolute address — the backend returns a path when
 *  monohull.public.base-url is unset, which we prefix with the current origin. */
function resolveWebhookUrl(url: string): string {
  return url.startsWith('/') ? `${window.location.origin}${url}` : url
}

function providerHint(provider: RepoProvider): string {
  switch (provider) {
    case 'GITHUB':
      return 'GitHub → Settings → Webhooks → Add webhook. Payload URL = the URL above, Content type application/json, Secret = the secret above, trigger on “Pull requests”.'
    case 'GITLAB':
      return 'GitLab → Settings → Webhooks. URL = the URL above, Secret token = the secret above, enable “Merge request events”.'
    case 'BITBUCKET':
      return 'Bitbucket → Repository settings → Webhooks → Add webhook. URL = the URL above (the secret is embedded in it), triggers = Pull Request Created / Updated.'
  }
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <Tooltip title={copied ? 'Copied' : 'Copy'} placement="top">
      <IconButton
        size="small"
        onClick={() => { navigator.clipboard.writeText(value).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1200) }) }}
        sx={{ color: copied ? '#22c55e' : '#64748b', p: 0.25 }}
      >
        <ContentCopyIcon sx={{ fontSize: '0.9rem' }} />
      </IconButton>
    </Tooltip>
  )
}

function SecretRow({ label, value, mask = false }: { label: string; value: string; mask?: boolean }) {
  const [show, setShow] = useState(!mask)
  const display = show ? value : '•'.repeat(Math.min(value.length, 32))
  return (
    <Box sx={{ mb: 1 }}>
      <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.62rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.25, p: 0.75, bgcolor: 'rgba(2, 6, 23, 0.5)', borderRadius: 1, border: '1px solid rgba(34, 211, 238, 0.06)' }}>
        <Typography sx={{ flex: 1, fontFamily: '"JetBrains Mono", monospace', fontSize: '0.65rem', color: '#94a3b8', wordBreak: 'break-all' }}>
          {display}
        </Typography>
        {mask && (
          <IconButton size="small" onClick={() => setShow(s => !s)} sx={{ color: '#64748b', p: 0.25 }}>
            {show ? <VisibilityOffIcon sx={{ fontSize: '0.9rem' }} /> : <VisibilityIcon sx={{ fontSize: '0.9rem' }} />}
          </IconButton>
        )}
        <CopyButton value={value} />
      </Box>
    </Box>
  )
}

export default function RepositoriesConfigPage() {
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<ConnectedRepositoryRequest>(emptyForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [buildsRepo, setBuildsRepo] = useState<{ id: number; name: string } | null>(null)

  const { data: repos = [], isLoading, error } = useQuery({ queryKey: ['repositories'], queryFn: getRepositories })
  const { data: imageConfigs = [] } = useQuery({ queryKey: ['imageConfigs'], queryFn: getImageConfigs })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['repositories'] })
  const onError = (err: any) => setFormError(err?.response?.data?.error || err?.message || 'Request failed')

  const createMut = useMutation({ mutationFn: createRepository, onSuccess: () => { invalidate(); closeDialog() }, onError })
  const updateMut = useMutation({
    mutationFn: ({ id, req }: { id: number; req: ConnectedRepositoryRequest }) => updateRepository(id, req),
    onSuccess: () => { invalidate(); closeDialog() }, onError,
  })
  const deleteMut = useMutation({ mutationFn: deleteRepository, onSuccess: invalidate })

  const openCreate = () => { setEditingId(null); setForm({ ...emptyForm, imageConfigId: imageConfigs[0]?.id ?? 0 }); setFormError(null); setDialogOpen(true) }
  const openEdit = (r: ConnectedRepository) => {
    setEditingId(r.id)
    setForm({
      name: r.name, provider: r.provider, authMethod: r.authMethod, repoUrl: r.repoUrl, repoFullName: r.repoFullName,
      defaultBranch: r.defaultBranch, buildMode: r.buildMode, imageConfigId: r.imageConfigId ?? 0,
      cloneUsername: r.cloneUsername ?? '', cloneToken: '', sshPrivateKey: '', sshPassphrase: '',
      maxConcurrent: r.maxConcurrent, enabled: r.enabled,
    })
    setFormError(null); setDialogOpen(true)
  }
  const closeDialog = () => { setDialogOpen(false); setEditingId(null) }

  const handleSubmit = () => {
    if (!form.name.trim() || !form.repoUrl.trim() || !form.repoFullName.trim()) {
      setFormError('Name, repository URL, and full name (owner/repo) are required.'); return
    }
    if (!form.imageConfigId) { setFormError('Pick an image config to build the PR source with.'); return }
    if (form.authMethod === 'SSH' && editingId === null && !form.sshPrivateKey?.trim()) {
      setFormError('Paste the SSH private (deploy) key to connect over SSH.'); return
    }
    setFormError(null)
    if (editingId !== null) updateMut.mutate({ id: editingId, req: form })
    else createMut.mutate(form)
  }

  const saving = createMut.isPending || updateMut.isPending

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4">Repositories</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            Connect a git repo so Monohull builds each PR from its branch
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Connect Repository</Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>Failed to load repositories.</Alert>}

      {isLoading && (
        <Grid container spacing={2.5}>
          {[1, 2, 3].map(i => (
            <Grid item xs={12} sm={6} md={4} key={i}><Skeleton variant="rounded" height={260} sx={{ borderRadius: 3 }} /></Grid>
          ))}
        </Grid>
      )}

      {!isLoading && repos.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 8, px: 3, borderRadius: 4, border: '1px dashed rgba(99, 102, 241, 0.2)', background: 'rgba(99, 102, 241, 0.03)' }}>
          <Typography variant="h6" sx={{ color: 'text.secondary', mb: 1 }}>No repositories connected</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
            Connect a repository to auto-build its PRs.
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Connect Your First Repository</Button>
        </Box>
      )}

      <Grid container spacing={2.5}>
        {repos.map(r => (
          <Grid item xs={12} sm={6} md={4} key={r.id}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flex: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="subtitle1" fontWeight={600}>{r.name}</Typography>
                  {!r.enabled && <Chip label="Disabled" size="small" sx={{ height: 20, fontSize: '0.62rem' }} />}
                </Box>
                <Box sx={{ display: 'flex', gap: 0.75, mb: 1, flexWrap: 'wrap' }}>
                  <Chip label={PROVIDERS.find(p => p.value === r.provider)?.label ?? r.provider} size="small" color="primary" variant="outlined" sx={{ height: 22, fontSize: '0.65rem' }} />
                  <Chip label={r.authMethod === 'SSH' ? 'ssh' : 'https'} size="small" color="secondary" variant="outlined" sx={{ height: 22, fontSize: '0.65rem' }} />
                  <Chip label={r.buildMode === 'BUILD_AND_ENV' ? 'build + env' : 'build only'} size="small" variant="outlined" sx={{ height: 22, fontSize: '0.65rem' }} />
                </Box>
                <Typography sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.7rem', color: '#64748b', wordBreak: 'break-all', mb: 1.5 }}>
                  {r.repoFullName} · {r.defaultBranch} · {r.imageConfigName ?? '—'}
                </Typography>
                <SecretRow label="Webhook URL" value={resolveWebhookUrl(r.webhookUrl)} />
                <SecretRow label="Webhook secret" value={r.webhookSecret} mask />
                <Typography variant="caption" sx={{ color: '#475569', fontSize: '0.65rem', display: 'block', mt: 0.5 }}>
                  {providerHint(r.provider)}
                </Typography>
              </CardContent>
              <CardActions sx={{ px: 2, pb: 1.5, pt: 0, gap: 0.5 }}>
                <Button size="small" variant="outlined" startIcon={<HistoryIcon sx={{ fontSize: '16px !important' }} />} onClick={() => setBuildsRepo({ id: r.id, name: r.name })}>Builds</Button>
                <Button size="small" variant="outlined" startIcon={<EditIcon sx={{ fontSize: '16px !important' }} />} onClick={() => openEdit(r)}>Edit</Button>
                <Button size="small" variant="outlined" color="error" startIcon={<DeleteIcon sx={{ fontSize: '16px !important' }} />}
                  onClick={() => { if (window.confirm(`Disconnect "${r.name}"? This removes its PR build history.`)) deleteMut.mutate(r.id) }}>
                  Delete
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>{editingId !== null ? 'Edit Repository' : 'Connect Repository'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Name" value={form.name} size="small" fullWidth required
              onChange={e => setForm({ ...form, name: e.target.value })} placeholder="e.g. maximo-config" />
            <Box sx={{ display: 'flex', gap: 2 }}>
              <FormControl fullWidth size="small" required>
                <InputLabel>Provider</InputLabel>
                <Select label="Provider" value={form.provider}
                  onChange={e => setForm({ ...form, provider: e.target.value as RepoProvider })}>
                  {PROVIDERS.map(p => <MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>)}
                </Select>
              </FormControl>
              <TextField label="Default branch" value={form.defaultBranch ?? ''} size="small" fullWidth
                onChange={e => setForm({ ...form, defaultBranch: e.target.value })} placeholder="main" />
            </Box>
            <FormControl fullWidth size="small" required>
              <InputLabel>Auth method</InputLabel>
              <Select label="Auth method" value={form.authMethod ?? 'HTTPS'}
                onChange={e => setForm({ ...form, authMethod: e.target.value as RepoAuthMethod })}>
                {AUTH_METHODS.map(a => <MenuItem key={a.value} value={a.value}>{a.label}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField label="Repository URL" value={form.repoUrl} size="small" fullWidth required
              onChange={e => setForm({ ...form, repoUrl: e.target.value })}
              placeholder={form.authMethod === 'SSH' ? 'git@bitbucket.org:acme/maximo-config.git' : 'https://bitbucket.org/acme/maximo-config.git'}
              helperText={form.authMethod === 'SSH' ? 'SSH clone URL (git@host:owner/repo.git or ssh://…)' : 'HTTPS clone URL'} />
            <TextField label="Full name (owner/repo)" value={form.repoFullName} size="small" fullWidth required
              onChange={e => setForm({ ...form, repoFullName: e.target.value })} placeholder="acme/maximo-config"
              helperText="Used to match incoming webhook payloads" />
            <FormControl fullWidth size="small" required>
              <InputLabel>Build mode</InputLabel>
              <Select label="Build mode" value={form.buildMode}
                onChange={e => setForm({ ...form, buildMode: e.target.value as RepoBuildMode })}>
                {BUILD_MODES.map(m => <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>)}
              </Select>
            </FormControl>
            <Typography variant="caption" sx={{ color: '#64748b', mt: -1 }}>
              {BUILD_MODES.find(m => m.value === form.buildMode)?.hint}
            </Typography>
            <FormControl fullWidth size="small" required>
              <InputLabel>Image config (build pipeline)</InputLabel>
              <Select label="Image config (build pipeline)" value={form.imageConfigId || ''}
                onChange={e => setForm({ ...form, imageConfigId: Number(e.target.value) })}>
                {imageConfigs.map(ic => <MenuItem key={ic.id} value={ic.id}>{ic.client}/{ic.project} ({ic.maximoVersion})</MenuItem>)}
              </Select>
            </FormControl>
            {/* Credential fields depend on auth method. autoComplete off / new-password keeps
                the browser from injecting saved logins; shrink keeps labels off any filled value. */}
            {form.authMethod === 'SSH' ? (
              <Stack spacing={2}>
                <TextField label="SSH private key" value={form.sshPrivateKey ?? ''} size="small" fullWidth
                  multiline minRows={4} maxRows={10}
                  autoComplete="off" InputLabelProps={{ shrink: true }}
                  onChange={e => setForm({ ...form, sshPrivateKey: e.target.value })}
                  placeholder={editingId !== null ? 'leave blank to keep' : '-----BEGIN OPENSSH PRIVATE KEY-----\n…'}
                  helperText="PEM deploy key with read access. Stored write-only."
                  InputProps={{ sx: { fontFamily: '"JetBrains Mono", monospace', fontSize: '0.7rem' } }} />
                <TextField label="Key passphrase" type="password" value={form.sshPassphrase ?? ''} size="small" fullWidth
                  autoComplete="new-password" InputLabelProps={{ shrink: true }}
                  onChange={e => setForm({ ...form, sshPassphrase: e.target.value })}
                  placeholder={editingId !== null ? 'leave blank to keep' : 'optional — only if the key is encrypted'} />
              </Stack>
            ) : (
              <Box sx={{ display: 'flex', gap: 2 }}>
                <TextField label="Clone username" value={form.cloneUsername ?? ''} size="small" fullWidth
                  autoComplete="off" InputLabelProps={{ shrink: true }}
                  onChange={e => setForm({ ...form, cloneUsername: e.target.value })} placeholder="x-token-auth / oauth2" />
                <TextField label="Clone token" type="password" value={form.cloneToken ?? ''} size="small" fullWidth
                  autoComplete="new-password" InputLabelProps={{ shrink: true }}
                  onChange={e => setForm({ ...form, cloneToken: e.target.value })}
                  placeholder={editingId !== null ? 'leave blank to keep' : 'app password / PAT'} />
              </Box>
            )}
            <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
              <TextField label="Max concurrent" type="number" value={form.maxConcurrent ?? 2} size="small" sx={{ width: 160 }}
                onChange={e => setForm({ ...form, maxConcurrent: Number(e.target.value) })} />
              <FormControlLabel control={<Switch checked={form.enabled ?? true}
                onChange={e => setForm({ ...form, enabled: e.target.checked })} />} label="Enabled" />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button onClick={handleSubmit} variant="contained" disabled={saving}>
            {saving ? 'Saving…' : editingId !== null ? 'Save' : 'Connect'}
          </Button>
        </DialogActions>
      </Dialog>

      <PrBuildsDialog open={!!buildsRepo} onClose={() => setBuildsRepo(null)}
        repoId={buildsRepo?.id ?? null} repoName={buildsRepo?.name ?? ''} />
    </>
  )
}
