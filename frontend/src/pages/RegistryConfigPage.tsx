import { useEffect, useState } from 'react'
import {
  Typography, Box, Button, Card, CardContent, TextField, Stack, Alert, Skeleton,
  Chip, Tooltip,
} from '@mui/material'
import SaveIcon from '@mui/icons-material/SaveRounded'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getRegistryCredential, saveRegistryCredential, deleteRegistryCredential,
  RegistryCredentialRequest,
} from '../api/client'

const emptyForm: RegistryCredentialRequest = {
  url: '', username: '', password: '', description: '',
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
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } }; message?: string }
      setError(e.response?.data?.message ?? e.message ?? 'Failed to save')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRegistryCredential,
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['registryCredential'] })
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
    </Box>
  )
}