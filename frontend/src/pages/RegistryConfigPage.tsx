import { useEffect, useState } from 'react'
import {
  Typography, Box, Button, Card, CardContent, TextField, Stack, Alert, Skeleton,
  Chip, Tooltip, IconButton, Collapse, CircularProgress,
} from '@mui/material'
import SaveIcon from '@mui/icons-material/SaveRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import ExpandMoreIcon from '@mui/icons-material/ExpandMoreRounded'
import CopyIcon from '@mui/icons-material/ContentCopyRounded'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getRegistryCredential, saveRegistryCredential, deleteRegistryCredential,
  getRegistryCatalog, getRegistryTags,
  RegistryCredentialRequest,
} from '../api/client'

const emptyForm: RegistryCredentialRequest = {
  url: '', username: '', password: '', description: '',
}

const mono = '"JetBrains Mono", monospace'

/** The API always reports failures as {"error": "..."}; fall back for anything that doesn't. */
const errorMessage = (err: unknown, fallback: string) => {
  const e = err as { response?: { data?: { error?: string; message?: string } }; message?: string }
  return e?.response?.data?.error ?? e?.response?.data?.message ?? e?.message ?? fallback
}

export default function RegistryConfigPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<RegistryCredentialRequest>(emptyForm)
  const [error, setError] = useState<string | null>(null)

  const { data: credential, isLoading } = useQuery({
    queryKey: ['registryCredential'],
    queryFn: getRegistryCredential,
  })

  useEffect(() => {
    if (credential) {
      setForm({
        url: credential.url,
        username: credential.username,
        password: '',
        description: credential.description ?? '',
      })
    } else {
      setForm(emptyForm)
    }
  }, [credential])

  const saveMutation = useMutation({
    mutationFn: saveRegistryCredential,
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['registryCredential'] })
      // Pointing at a different registry (or fixing a password) invalidates what we listed.
      queryClient.invalidateQueries({ queryKey: ['registryCatalog'] })
      queryClient.invalidateQueries({ queryKey: ['registryTags'] })
    },
    onError: (err: unknown) => setError(errorMessage(err, 'Failed to save')),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRegistryCredential,
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['registryCredential'] })
      queryClient.removeQueries({ queryKey: ['registryCatalog'] })
      queryClient.removeQueries({ queryKey: ['registryTags'] })
    },
  })

  const handleSave = () => {
    if (!form.url.trim() || !form.username.trim()) {
      setError('URL and username are required')
      return
    }
    if (!credential && !form.password?.trim()) {
      setError('Password is required when creating credentials')
      return
    }
    const payload: RegistryCredentialRequest = {
      url: form.url.trim(),
      username: form.username.trim(),
      description: form.description?.trim() || undefined,
    }
    if (form.password && form.password.trim()) {
      payload.password = form.password
    }
    saveMutation.mutate(payload)
  }

  const handleDelete = () => {
    if (!confirm('Delete the registry credential? Image pulls from this registry will fail unless the host already has them configured.')) return
    deleteMutation.mutate()
  }

  return (
    <Box>
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
        <Box>
          <Typography variant="h4" fontWeight={700} sx={{ letterSpacing: '-0.02em' }}>
            Registry Credentials
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Credentials used to pull container images from a private Docker registry.
          </Typography>
        </Box>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>{error}</Alert>}

      {isLoading ? (
        <Skeleton variant="rounded" height={320} />
      ) : (
        <Card sx={{ maxWidth: 720 }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="subtitle2" sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', color: 'text.secondary' }}>
                  Configuration
                </Typography>
                <Box sx={{ flex: 1, borderBottom: '1px solid rgba(148, 163, 184, 0.08)' }} />
                {credential ? (
                  <Chip size="small" color="success" label="Configured" />
                ) : (
                  <Chip size="small" label="Not configured" />
                )}
              </Box>

              <TextField
                label="Registry URL"
                placeholder="e.g. registry.example.com or registry.example.com:5000"
                value={form.url}
                onChange={e => setForm({ ...form, url: e.target.value })}
                fullWidth
                helperText="Hostname (and optional port) of the private registry. Credentials are only sent for images whose host matches this value."
              />

              <TextField
                label="Username"
                value={form.username}
                onChange={e => setForm({ ...form, username: e.target.value })}
                fullWidth
              />

              <TextField
                label={credential ? 'Password (leave blank to keep existing)' : 'Password'}
                type="password"
                value={form.password ?? ''}
                onChange={e => setForm({ ...form, password: e.target.value })}
                fullWidth
                autoComplete="new-password"
              />

              <TextField
                label="Description (optional)"
                value={form.description ?? ''}
                onChange={e => setForm({ ...form, description: e.target.value })}
                fullWidth
              />

              <Stack direction="row" spacing={1.5} sx={{ pt: 1 }}>
                <Button
                  variant="contained"
                  startIcon={<SaveIcon />}
                  onClick={handleSave}
                  disabled={saveMutation.isPending}
                >
                  {credential ? 'Update' : 'Save'}
                </Button>
                {credential && (
                  <Tooltip title="Remove the stored credentials">
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<DeleteIcon />}
                      onClick={handleDelete}
                      disabled={deleteMutation.isPending}
                    >
                      Delete
                    </Button>
                  </Tooltip>
                )}
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      )}

      {!isLoading && credential && <AvailableImages registry={credential.url} />}
    </Box>
  )
}

/**
 * Browses the configured registry's catalog (MH-20). Only rendered once credentials exist,
 * since every call here authenticates with them.
 */
function AvailableImages({ registry }: { registry: string }) {
  const { data, isLoading, isFetching, error, refetch } = useQuery({
    queryKey: ['registryCatalog'],
    queryFn: getRegistryCatalog,
    retry: false,
  })

  return (
    <Box sx={{ mt: 5, maxWidth: 720 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Typography variant="subtitle2" sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', color: 'text.secondary' }}>
          Available Images
        </Typography>
        <Box sx={{ flex: 1, borderBottom: '1px solid rgba(148, 163, 184, 0.08)' }} />
        {data && !error && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {data.repositories.length} {data.repositories.length === 1 ? 'repository' : 'repositories'}
          </Typography>
        )}
        <Tooltip title="Refresh from the registry">
          <span>
            <IconButton size="small" onClick={() => refetch()} disabled={isFetching}>
              <RefreshIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Box>

      {isLoading ? (
        <Stack spacing={1}>
          <Skeleton variant="rounded" height={44} />
          <Skeleton variant="rounded" height={44} />
          <Skeleton variant="rounded" height={44} />
        </Stack>
      ) : error ? (
        <Alert severity="warning" action={<Button size="small" onClick={() => refetch()}>Retry</Button>}>
          {errorMessage(error, 'Could not read the registry catalog')}
        </Alert>
      ) : !data || data.repositories.length === 0 ? (
        <Box sx={{ p: 4, textAlign: 'center', borderRadius: 2, border: '1px dashed rgba(99, 102, 241, 0.2)' }}>
          <Typography variant="body2" color="text.secondary">
            The registry is reachable but has no repositories yet.
          </Typography>
        </Box>
      ) : (
        <>
          {data.truncated && (
            <Alert severity="info" sx={{ mb: 2 }}>
              This registry has more repositories than Monohull lists in one go — showing the
              first {data.repositories.length}.
            </Alert>
          )}
          <Card>
            <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
              {data.repositories.map((repo, i) => (
                <RepositoryRow key={repo} repository={repo} registry={registry} divider={i > 0} />
              ))}
            </CardContent>
          </Card>
        </>
      )}
    </Box>
  )
}

/** One repository; its tags are fetched only when the row is first expanded. */
function RepositoryRow({ repository, registry, divider }: { repository: string; registry: string; divider: boolean }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['registryTags', repository],
    queryFn: () => getRegistryTags(repository),
    enabled: open,
    retry: false,
  })

  const copyRef = (tag: string) => {
    const ref = `${registry}/${repository}:${tag}`
    navigator.clipboard?.writeText(ref).then(
      () => {
        setCopied(tag)
        setTimeout(() => setCopied(null), 1500)
      },
      () => undefined,
    )
  }

  return (
    <Box sx={{ borderTop: divider ? '1px solid rgba(148, 163, 184, 0.08)' : 'none' }}>
      <Box
        onClick={() => setOpen(o => !o)}
        sx={{
          display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1.25, cursor: 'pointer',
          '&:hover': { bgcolor: 'rgba(99, 102, 241, 0.04)' },
        }}
      >
        <ExpandMoreIcon
          fontSize="small"
          sx={{
            color: 'text.secondary',
            transform: open ? 'rotate(0deg)' : 'rotate(-90deg)',
            transition: 'transform 150ms',
          }}
        />
        <Typography sx={{ fontFamily: mono, fontSize: '0.8rem', flex: 1, wordBreak: 'break-all' }}>
          {repository}
        </Typography>
        {open && data && (
          <Chip size="small" label={`${data.tags.length} ${data.tags.length === 1 ? 'tag' : 'tags'}`} />
        )}
      </Box>

      <Collapse in={open} unmountOnExit>
        <Box sx={{ px: 2, pb: 2, pl: 5 }}>
          {isLoading ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}>
              <CircularProgress size={14} />
              <Typography variant="caption" color="text.secondary">Loading tags…</Typography>
            </Box>
          ) : error ? (
            <Typography variant="caption" color="error">
              {errorMessage(error, 'Could not read tags for this repository')}
            </Typography>
          ) : !data || data.tags.length === 0 ? (
            <Typography variant="caption" color="text.secondary">
              No tags — the repository exists but holds no images.
            </Typography>
          ) : (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {data.tags.map(tag => (
                <Tooltip key={tag} title={copied === tag ? 'Copied!' : `Copy ${registry}/${repository}:${tag}`}>
                  <Chip
                    size="small"
                    label={tag}
                    onClick={() => copyRef(tag)}
                    deleteIcon={<CopyIcon sx={{ fontSize: '0.75rem !important' }} />}
                    onDelete={() => copyRef(tag)}
                    sx={{
                      fontFamily: mono,
                      fontSize: '0.7rem',
                      borderColor: copied === tag ? 'success.main' : undefined,
                    }}
                    variant="outlined"
                  />
                </Tooltip>
              ))}
            </Box>
          )}
        </Box>
      </Collapse>
    </Box>
  )
}