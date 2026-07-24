import { Box, Typography, IconButton, TextField, FormControlLabel, Checkbox, Button, Stack } from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineRounded'
import { ExtraBind, ExtraEnvVar } from '../api/client'

interface Props {
  title: string
  envVars: ExtraEnvVar[]
  binds: ExtraBind[]
  onChange: (next: { envVars: ExtraEnvVar[]; binds: ExtraBind[] }) => void
}

const sectionLabelSx = {
  textTransform: 'uppercase',
  letterSpacing: '0.06em',
  color: '#94a3b8',
  fontSize: '0.7rem',
  fontWeight: 600,
  display: 'block',
  mb: 1,
}

export default function ContainerExtrasEditor({ title, envVars, binds, onChange }: Props) {
  const updateEnv = (next: ExtraEnvVar[]) => onChange({ envVars: next, binds })
  const updateBinds = (next: ExtraBind[]) => onChange({ envVars, binds: next })

  const addEnv = () => updateEnv([...envVars, { key: '', value: '' }])
  const removeEnv = (idx: number) => updateEnv(envVars.filter((_, i) => i !== idx))
  const setEnv = (idx: number, patch: Partial<ExtraEnvVar>) =>
    updateEnv(envVars.map((e, i) => (i === idx ? { ...e, ...patch } : e)))

  const addBind = () => updateBinds([...binds, { hostPath: '', containerPath: '', readOnly: false }])
  const removeBind = (idx: number) => updateBinds(binds.filter((_, i) => i !== idx))
  const setBind = (idx: number, patch: Partial<ExtraBind>) =>
    updateBinds(binds.map((b, i) => (i === idx ? { ...b, ...patch } : b)))

  return (
    <Box
      sx={{
        border: '1px solid rgba(99, 102, 241, 0.12)',
        borderRadius: 2,
        p: 2,
        background: 'rgba(99, 102, 241, 0.03)',
      }}
    >
      <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
        {title}
      </Typography>

      <Typography sx={sectionLabelSx}>Environment variables</Typography>
      {envVars.length === 0 ? (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          None
        </Typography>
      ) : (
        <Stack spacing={1} sx={{ mb: 1 }}>
          {envVars.map((e, i) => (
            <Stack key={i} direction="row" spacing={1} alignItems="center">
              <TextField
                label="Key"
                size="small"
                value={e.key}
                onChange={ev => setEnv(i, { key: ev.target.value })}
                sx={{ flex: '0 0 200px' }}
                InputLabelProps={{ shrink: true }}
                placeholder="AWS_PROFILE"
              />
              <TextField
                label="Value"
                size="small"
                value={e.value}
                onChange={ev => setEnv(i, { value: ev.target.value })}
                fullWidth
                InputLabelProps={{ shrink: true }}
                placeholder="my-aws-profile"
              />
              <IconButton size="small" onClick={() => removeEnv(i)} sx={{ color: '#94a3b8' }}>
                <DeleteOutlineIcon />
              </IconButton>
            </Stack>
          ))}
        </Stack>
      )}
      <Button size="small" startIcon={<AddIcon />} onClick={addEnv} sx={{ mb: 2 }}>
        Add env var
      </Button>

      <Typography sx={sectionLabelSx}>Volume mounts</Typography>
      {binds.length === 0 ? (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          None
        </Typography>
      ) : (
        <Stack spacing={1} sx={{ mb: 1 }}>
          {binds.map((b, i) => (
            <Stack key={i} direction="row" spacing={1} alignItems="center">
              <TextField
                label="Host path"
                size="small"
                value={b.hostPath}
                onChange={ev => setBind(i, { hostPath: ev.target.value })}
                fullWidth
                InputLabelProps={{ shrink: true }}
                placeholder="C:\\Users\\you\\.aws"
              />
              <TextField
                label="Container path"
                size="small"
                value={b.containerPath}
                onChange={ev => setBind(i, { containerPath: ev.target.value })}
                fullWidth
                InputLabelProps={{ shrink: true }}
                placeholder="/root/.aws"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    size="small"
                    checked={b.readOnly}
                    onChange={ev => setBind(i, { readOnly: ev.target.checked })}
                  />
                }
                label="ro"
                sx={{ mr: 0, '& .MuiFormControlLabel-label': { fontSize: '0.75rem' } }}
              />
              <IconButton size="small" onClick={() => removeBind(i)} sx={{ color: '#94a3b8' }}>
                <DeleteOutlineIcon />
              </IconButton>
            </Stack>
          ))}
        </Stack>
      )}
      <Button size="small" startIcon={<AddIcon />} onClick={addBind}>
        Add mount
      </Button>

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, fontSize: '0.7rem' }}>
        Use absolute host paths. Windows drive paths (e.g. <code>F:\foo</code>) are auto-converted to <code>/f/foo</code> for Docker Desktop.
      </Typography>
    </Box>
  )
}
