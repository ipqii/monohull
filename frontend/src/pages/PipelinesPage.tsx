import { useState, useMemo, useCallback } from 'react'
import {
  Typography, Box, Button, TextField, Alert, Paper, Stack, Chip, IconButton, Tooltip,
  FormControl, InputLabel, Select, MenuItem, SelectChangeEvent,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/DeleteOutlineRounded'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import SaveIcon from '@mui/icons-material/SaveRounded'
import AddIcon from '@mui/icons-material/AddRounded'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getPipelines, getPipeline, createPipeline, updatePipeline, deletePipeline,
  getCustomActions, getEnvironments, PipelineDefinition, CustomAction,
  EnvironmentResponse,
} from '../api/client'
import {
  DndContext, DragOverlay, useDraggable, useDroppable,
  DragStartEvent, DragMoveEvent, DragEndEvent, pointerWithin, rectIntersection,
  PointerSensor, TouchSensor, useSensor, useSensors,
  type CollisionDetection,
} from '@dnd-kit/core'
import {
  SortableContext, useSortable, verticalListSortingStrategy, arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import yaml from 'js-yaml'
import ActionDefinitionLink from '../components/ActionDefinitionLink'

interface PipelineStepLocal {
  instanceId: string
  actionKey: string
  actionName: string
  targetRole: string
}

/** Vertical margin between step cards — the gap that opens has to account for it. */
const STEP_CARD_GAP = 8

/**
 * Where a card dragged in from the palette would land, or null when the pointer isn't over the
 * pipeline. Insert before or after the hovered step depending on which half the pointer is in,
 * so the last position is reachable without having to find empty space below the list.
 *
 * `over.rect` is measured ignoring transforms, so the midpoint stays where the step was before
 * the gap opened — the answer can't flip back and forth as the cards move out of the way.
 */
function insertionPointFor(
  event: DragMoveEvent | DragEndEvent,
  steps: PipelineStepLocal[],
): { index: number; gap: number } | null {
  const { over, delta, activatorEvent } = event
  if (!over) return null
  if (over.id === 'pipeline-drop-area') return { index: steps.length, gap: 0 }

  const hovered = steps.findIndex(s => s.instanceId === over.id)
  if (hovered < 0) return null

  const gap = over.rect.height + STEP_CARD_GAP
  const pointerY = activatorPointerY(activatorEvent)
  if (pointerY == null) return { index: hovered, gap }

  const belowMidpoint = pointerY + delta.y > over.rect.top + over.rect.height / 2
  return { index: belowMidpoint ? hovered + 1 : hovered, gap }
}

function activatorPointerY(event: Event): number | null {
  if ('clientY' in event) return (event as PointerEvent).clientY
  const touch = (event as TouchEvent).touches?.[0] ?? (event as TouchEvent).changedTouches?.[0]
  return touch ? touch.clientY : null
}

type EditorMode = 'form' | 'yaml'

function pipelineToYaml(
  name: string,
  description: string,
  environmentId: number | null,
  steps: PipelineStepLocal[],
  environments: EnvironmentResponse[],
): string {
  const obj: Record<string, unknown> = { name: name || '' }
  if (description) obj.description = description
  if (environmentId != null) {
    obj.environmentId = environmentId
    const env = environments.find(e => e.id === environmentId)
    if (env) obj['# environment'] = env.name
  }
  obj.steps = steps.map(s => ({ actionKey: s.actionKey }))
  return yaml.dump(obj, { lineWidth: -1 })
}

function yamlToPipeline(
  text: string,
  actionsByKey: Map<string, CustomAction>
): { name: string; description: string; environmentId: number | null; steps: PipelineStepLocal[] } {
  const obj = yaml.load(text) as Record<string, unknown>
  if (!obj || typeof obj !== 'object') throw new Error('Invalid YAML')
  const rawSteps = Array.isArray(obj.steps) ? obj.steps : []
  const steps: PipelineStepLocal[] = rawSteps.map((s: unknown) => {
    const step = s as Record<string, unknown>
    const actionKey = String(step.actionKey ?? '')
    const action = actionsByKey.get(actionKey)
    return {
      instanceId: Math.random().toString(36).slice(2) + Date.now().toString(36),
      actionKey,
      actionName: action?.name ?? actionKey,
      targetRole: action?.targetRole ?? '—',
    }
  })
  return {
    name: String(obj.name ?? ''),
    description: obj.description ? String(obj.description) : '',
    environmentId: obj.environmentId != null ? Number(obj.environmentId) : null,
    steps,
  }
}

function downloadYaml(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/yaml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function DraggableActionCard({ action }: { action: CustomAction }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `action-${action.actionKey}`,
    data: { type: 'action-source', action },
  })

  const style = {
    transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
    opacity: isDragging ? 0.4 : 1,
  }

  return (
    <Tooltip title={action.description || ''} placement="left" enterDelay={400} disableHoverListener={!action.description}>
      <Paper
        ref={setNodeRef}
        {...listeners}
        {...attributes}
        data-testid="palette-action"
        sx={{
          p: 1.5,
          mb: 1,
          cursor: 'grab',
          transition: 'all 0.15s ease',
          '&:hover': { borderColor: 'rgba(99, 102, 241, 0.2)', bgcolor: 'rgba(99, 102, 241, 0.04)' },
        }}
        style={style}
        variant="outlined"
      >
        <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}>
          <DragIndicatorIcon fontSize="small" sx={{ color: '#475569', flexShrink: 0 }} />
          <Box sx={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
            <Typography variant="body2" fontWeight={500} noWrap>{action.name}</Typography>
            {action.description && (
              <Typography
                variant="caption"
                color="text.secondary"
                noWrap
                display="block"
                sx={{ fontSize: '0.7rem', overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
                {action.description}
              </Typography>
            )}
          </Box>
          <Chip
            label={action.targetRole}
            size="small"
            sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(99,102,241,0.1)', color: '#818cf8', border: 'none', flexShrink: 0 }}
          />
          <ActionDefinitionLink actionId={action.id} />
        </Stack>
      </Paper>
    </Tooltip>
  )
}

function SortableStepCard({ step, index, actionId, shift, onRemove }: {
  step: PipelineStepLocal; index: number; actionId: number | null; shift: number; onRemove: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: step.instanceId,
    data: { type: 'pipeline-step' },
  })

  // `shift` opens the gap for an action being dragged in from the palette. Sortable's own
  // transform is null in that case (the dragged item isn't one of its items), but compose them
  // anyway so the two can never fight.
  const sortableTransform = CSS.Transform.toString(transform)
  const style = {
    transform: shift
      ? [sortableTransform, `translate3d(0, ${shift}px, 0)`].filter(Boolean).join(' ')
      : sortableTransform,
    transition: transition ?? 'transform 180ms cubic-bezier(0.2, 0, 0, 1)',
    opacity: isDragging ? 0.4 : 1,
  }

  return (
    <Paper
      ref={setNodeRef}
      style={style}
      data-testid="pipeline-step"
      sx={{
        p: 1.5,
        mb: 1,
        borderLeft: '3px solid #6366f1',
      }}
      variant="outlined"
    >
      <Stack direction="row" alignItems="center" spacing={1}>
        <Box {...attributes} {...listeners} sx={{ cursor: 'grab', display: 'flex' }}>
          <DragIndicatorIcon fontSize="small" sx={{ color: '#475569' }} />
        </Box>
        <Box
          sx={{
            width: 22, height: 22, borderRadius: '50%',
            bgcolor: 'rgba(99,102,241,0.12)', display: 'flex',
            alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}
        >
          <Typography variant="caption" sx={{ fontSize: '0.65rem', fontWeight: 700, color: '#818cf8' }}>
            {index + 1}
          </Typography>
        </Box>
        <Tooltip title={step.actionKey} placement="top">
          <Typography variant="body2" fontWeight={500} sx={{ flex: 1 }}>{step.actionName}</Typography>
        </Tooltip>
        <Chip
          label={step.targetRole}
          size="small"
          variant="outlined"
          sx={{ fontSize: '0.65rem', height: 20, borderColor: 'rgba(148,163,184,0.15)' }}
        />
        <ActionDefinitionLink actionId={actionId} />
        <Tooltip title="Remove step">
          <IconButton size="small" onClick={onRemove} sx={{ color: '#64748b', '&:hover': { color: '#ef4444' } }}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
    </Paper>
  )
}

function DroppablePipelineArea({ tailSpace, children }: { tailSpace: number; children: React.ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: 'pipeline-drop-area' })
  return (
    <Box
      ref={setNodeRef}
      sx={{
        minHeight: 200,
        p: 1.5,
        // Cards make way with a transform, which doesn't grow the box — pad the bottom by the
        // same amount so the last one doesn't hang below the dashed border.
        pb: tailSpace ? `${tailSpace + 12}px` : 1.5,
        border: '2px dashed',
        borderColor: isOver ? 'rgba(99, 102, 241, 0.4)' : 'rgba(148, 163, 184, 0.08)',
        borderRadius: 2.5,
        transition: 'all 0.2s ease',
        bgcolor: isOver ? 'rgba(99, 102, 241, 0.03)' : 'transparent',
      }}
    >
      {children}
    </Box>
  )
}

export default function PipelinesPage() {
  const queryClient = useQueryClient()

  const [selectedPipelineId, setSelectedPipelineId] = useState<number | ''>('')
  const [pipelineName, setPipelineName] = useState('')
  const [pipelineDescription, setPipelineDescription] = useState('')
  const [pipelineEnvId, setPipelineEnvId] = useState<number | null>(null)
  const [steps, setSteps] = useState<PipelineStepLocal[]>([])
  const [activeAction, setActiveAction] = useState<CustomAction | null>(null)
  const [insertion, setInsertion] = useState<{ index: number; gap: number } | null>(null)
  const [editorMode, setEditorMode] = useState<EditorMode>('form')
  const [yamlText, setYamlText] = useState('')
  const [yamlError, setYamlError] = useState<string | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    // Touch needs a delay so a finger landing on a card and then scrolling doesn't accidentally
    // start a drag. Tolerance keeps the gesture from getting cancelled by tiny finger jitter.
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
  )

  const collisionDetection: CollisionDetection = (args) => {
    const pointerCollisions = pointerWithin(args)
    if (pointerCollisions.length > 0) {
      const pipelineHit = pointerCollisions.find(
        c => c.id === 'pipeline-drop-area' || steps.some(s => s.instanceId === c.id)
      )
      if (pipelineHit) return [pipelineHit]
      return pointerCollisions
    }
    return rectIntersection(args)
  }

  const { data: pipelines = [] } = useQuery({
    queryKey: ['pipelines'],
    queryFn: getPipelines,
  })

  const { data: actions = [] } = useQuery({
    queryKey: ['customActions'],
    queryFn: getCustomActions,
  })

  const { data: environments = [] } = useQuery({
    queryKey: ['environments'],
    queryFn: getEnvironments,
  })

  const visibleActions = useMemo(() => {
    // Pipeline scope decides what's draggable. Global pipeline sees only global + image-config-scoped
    // actions; env-scoped pipeline sees those plus actions for that env.
    return actions.filter(a => {
      if (a.environmentId != null) return pipelineEnvId === a.environmentId
      return true
    })
  }, [actions, pipelineEnvId])

  const actionsByKey = useMemo(() => {
    const map = new Map<string, CustomAction>()
    actions.forEach(a => map.set(a.actionKey, a))
    return map
  }, [actions])

  const applyPipeline = (
    name: string,
    description: string,
    envId: number | null,
    loadedSteps: PipelineStepLocal[],
  ) => {
    setPipelineName(name)
    setPipelineDescription(description)
    setPipelineEnvId(envId)
    setSteps(loadedSteps)
    setYamlText(pipelineToYaml(name, description, envId, loadedSteps, environments))
    setYamlError(null)
  }

  const loadPipeline = (pipeline: PipelineDefinition) => {
    setSelectedPipelineId(pipeline.id)
    applyPipeline(
      pipeline.name,
      pipeline.description || '',
      pipeline.environmentId ?? null,
      pipeline.steps.map(s => ({
        instanceId: Math.random().toString(36).slice(2) + Date.now().toString(36),
        actionKey: s.actionKey,
        actionName: s.actionName,
        targetRole: s.targetRole,
      }))
    )
  }

  const handlePipelineSelect = (e: SelectChangeEvent<number | ''>) => {
    const val = e.target.value
    if (val === '') {
      handleNew()
      return
    }
    const id = val as number
    const cached = pipelines.find(p => p.id === id)
    if (cached) {
      getPipeline(id).then(loadPipeline).catch(() => {
        if (cached) loadPipeline(cached)
      })
    }
  }

  const handleNew = () => {
    setSelectedPipelineId('')
    applyPipeline('', '', null, [])
  }

  const handleEditorModeSwitch = useCallback((_: unknown, newMode: EditorMode | null) => {
    if (!newMode) return
    setYamlError(null)
    if (newMode === 'yaml') {
      setYamlText(pipelineToYaml(pipelineName, pipelineDescription, pipelineEnvId, steps, environments))
    } else {
      try {
        const parsed = yamlToPipeline(yamlText, actionsByKey)
        setPipelineName(parsed.name)
        setPipelineDescription(parsed.description)
        setPipelineEnvId(parsed.environmentId)
        setSteps(parsed.steps)
      } catch {
        setYamlError('Invalid YAML. Fix errors before switching to form view.')
        return
      }
    }
    setEditorMode(newMode)
  }, [pipelineName, pipelineDescription, pipelineEnvId, steps, yamlText, actionsByKey, environments])

  const handleExport = () => {
    const content = editorMode === 'yaml'
      ? yamlText
      : pipelineToYaml(pipelineName, pipelineDescription, pipelineEnvId, steps, environments)
    const filename = (pipelineName.trim() || 'pipeline').toLowerCase().replace(/[^a-z0-9-]/g, '-')
    downloadYaml(content, `${filename}.pipeline.yaml`)
  }

  const createMutation = useMutation({
    mutationFn: createPipeline,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['pipelines'] })
      setSelectedPipelineId(data.id)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: number; req: Parameters<typeof updatePipeline>[1] }) => updatePipeline(id, req),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pipelines'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deletePipeline,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pipelines'] })
      handleNew()
    },
  })

  const handleSave = () => {
    let name = pipelineName.trim()
    let description = pipelineDescription.trim()
    let envId = pipelineEnvId
    let saveSteps = steps

    if (editorMode === 'yaml') {
      try {
        const parsed = yamlToPipeline(yamlText, actionsByKey)
        name = parsed.name.trim()
        description = parsed.description.trim()
        envId = parsed.environmentId
        saveSteps = parsed.steps
        setPipelineName(parsed.name)
        setPipelineDescription(parsed.description)
        setPipelineEnvId(parsed.environmentId)
        setSteps(parsed.steps)
        setYamlError(null)
      } catch (e: unknown) {
        setYamlError(e instanceof Error ? e.message : 'Invalid YAML')
        return
      }
    }

    if (!name) return
    const req = {
      name,
      description: description || undefined,
      environmentId: envId,
      steps: saveSteps.map((s, i) => ({ actionKey: s.actionKey, sequenceOrder: i })),
    }
    if (selectedPipelineId) {
      updateMutation.mutate({ id: selectedPipelineId as number, req })
    } else {
      createMutation.mutate(req)
    }
  }

  const handleDelete = () => {
    if (!selectedPipelineId) return
    if (window.confirm(`Delete pipeline "${pipelineName}"?`)) {
      deleteMutation.mutate(selectedPipelineId as number)
    }
  }

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current
    if (data?.type === 'action-source') {
      setActiveAction(data.action as CustomAction)
    } else {
      setActiveAction(null)
    }
    setInsertion(null)
  }

  // Shows where the dragged action would land by moving the steps below it out of the way.
  // Only for palette drags — reordering an existing step is already animated by useSortable.
  const handleDragMove = (event: DragMoveEvent) => {
    if (event.active.data.current?.type !== 'action-source') return
    setInsertion(prev => {
      const next = insertionPointFor(event, steps)
      if (prev?.index === next?.index && prev?.gap === next?.gap) return prev
      return next
    })
  }

  const handleDragCancel = () => {
    setActiveAction(null)
    setInsertion(null)
  }

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveAction(null)
    setInsertion(null)
    const { active, over } = event

    if (!over) return

    const activeData = active.data.current

    if (activeData?.type === 'action-source') {
      // Recomputed rather than read off state so the drop always lands in the gap the user saw,
      // even if the pointer never moved far enough to fire a drag-move.
      const target = insertionPointFor(event, steps)
      if (!target) return

      const action = activeData.action as CustomAction
      const newStep: PipelineStepLocal = {
        instanceId: Math.random().toString(36).slice(2) + Date.now().toString(36),
        actionKey: action.actionKey,
        actionName: action.name,
        targetRole: action.targetRole,
      }

      setSteps(prev => {
        const copy = [...prev]
        copy.splice(Math.min(target.index, copy.length), 0, newStep)
        return copy
      })
      return
    }

    if (activeData?.type === 'pipeline-step') {
      const oldIndex = steps.findIndex(s => s.instanceId === active.id)
      const newIndex = steps.findIndex(s => s.instanceId === over.id)
      if (oldIndex !== -1 && newIndex !== -1 && oldIndex !== newIndex) {
        setSteps(prev => arrayMove(prev, oldIndex, newIndex))
      }
    }
  }

  const removeStep = (instanceId: string) => {
    setSteps(prev => prev.filter(s => s.instanceId !== instanceId))
  }

  const isSaving = createMutation.isPending || updateMutation.isPending

  const toggleButtonSx = {
    '& .MuiToggleButton-root': {
      textTransform: 'none',
      fontWeight: 500,
      fontSize: '0.8rem',
      px: 1.5,
      py: 0.25,
      borderColor: 'rgba(99,102,241,0.2)',
      '&.Mui-selected': { bgcolor: 'rgba(99,102,241,0.12)', color: '#818cf8' },
    },
  }

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'stretch', md: 'center' }, mb: 4, flexDirection: { xs: 'column', md: 'row' }, gap: 2 }}>
        <Box>
          <Typography variant="h4">Pipelines</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            Build ordered sequences of actions
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: 1, flexWrap: 'wrap', alignItems: { xs: 'stretch', sm: 'center' } }}>
          <FormControl size="small" sx={{ width: { xs: '100%', sm: 200 } }}>
            <InputLabel>Load Pipeline</InputLabel>
            <Select
              value={selectedPipelineId}
              label="Load Pipeline"
              onChange={handlePipelineSelect}
            >
              <MenuItem value="">
                <em>New Pipeline</em>
              </MenuItem>
              {pipelines.map(p => (
                <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Button variant="outlined" startIcon={<AddIcon />} onClick={handleNew} size="small">
              New
            </Button>
            <Tooltip title="Export YAML">
              <Button variant="outlined" startIcon={<DownloadIcon />} onClick={handleExport} size="small">
                Export
              </Button>
            </Tooltip>
            <Button
              variant="contained"
              startIcon={<SaveIcon />}
              onClick={handleSave}
              disabled={!pipelineName.trim() && !(editorMode === 'yaml' && yamlText.trim()) || isSaving}
              size="small"
            >
              {isSaving ? 'Saving...' : 'Save'}
            </Button>
            {selectedPipelineId && (
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteIcon />}
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                size="small"
              >
                Delete
              </Button>
            )}
          </Stack>
        </Box>
      </Box>

      {(createMutation.isError || updateMutation.isError || deleteMutation.isError) && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {(createMutation.error || updateMutation.error || deleteMutation.error)?.message || 'Operation failed'}
        </Alert>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={collisionDetection}
        onDragStart={handleDragStart}
        onDragMove={handleDragMove}
        onDragEnd={handleDragEnd}
        onDragCancel={handleDragCancel}
      >
        <Box sx={{ display: 'flex', gap: { xs: 2, md: 3 }, flexDirection: { xs: 'column', md: 'row' }, alignItems: { xs: 'stretch', md: 'flex-start' } }}>
          {/* Left panel — Pipeline */}
          <Box sx={{ flex: 3, minWidth: 0 }}>
            <Paper sx={{ p: { xs: 1.5, sm: 2.5 } }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 1, flexWrap: 'wrap' }}>
                <Typography variant="subtitle2" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.75rem' }}>
                  Pipeline Builder
                </Typography>
                <ToggleButtonGroup
                  value={editorMode}
                  exclusive
                  onChange={handleEditorModeSwitch}
                  size="small"
                  sx={toggleButtonSx}
                >
                  <ToggleButton value="form">Form</ToggleButton>
                  <ToggleButton value="yaml">YAML</ToggleButton>
                </ToggleButtonGroup>
              </Box>

              {yamlError && <Alert severity="error" sx={{ mb: 2, fontSize: '0.82rem' }}>{yamlError}</Alert>}

              {editorMode === 'form' ? (
                <>
                  <Stack spacing={2} sx={{ mb: 2.5 }}>
                    <TextField
                      label="Pipeline Name"
                      value={pipelineName}
                      onChange={e => setPipelineName(e.target.value)}
                      fullWidth
                      required
                      size="small"
                    />
                    <FormControl fullWidth size="small">
                      <InputLabel>Scope</InputLabel>
                      <Select
                        value={pipelineEnvId == null ? '' : String(pipelineEnvId)}
                        label="Scope"
                        onChange={e => {
                          const v = e.target.value
                          const next = v === '' ? null : Number(v)
                          if (next !== pipelineEnvId) {
                            // Drop steps whose action isn't visible under the new scope.
                            setSteps(prev => prev.filter(s => {
                              const a = actionsByKey.get(s.actionKey)
                              if (!a) return true
                              return a.environmentId == null || a.environmentId === next
                            }))
                          }
                          setPipelineEnvId(next)
                        }}
                      >
                        <MenuItem value="">Global (any environment)</MenuItem>
                        {environments.map(env => (
                          <MenuItem key={env.id} value={String(env.id)}>{env.name}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <TextField
                      label="Description (optional)"
                      value={pipelineDescription}
                      onChange={e => setPipelineDescription(e.target.value)}
                      fullWidth
                      size="small"
                      multiline
                      minRows={2}
                    />
                  </Stack>

                  <Typography variant="subtitle2" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.7rem', mb: 1.5 }}>
                    Steps
                  </Typography>
                  {/* Padding both keeps shifted cards inside the border and, when the insertion
                      point is the end of the list, is itself the gap. */}
                  <DroppablePipelineArea tailSpace={insertion ? insertion.gap : 0}>
                    <SortableContext items={steps.map(s => s.instanceId)} strategy={verticalListSortingStrategy}>
                      {steps.map((step, i) => (
                        <SortableStepCard
                          key={step.instanceId}
                          step={step}
                          index={i}
                          actionId={actionsByKey.get(step.actionKey)?.id ?? null}
                          shift={insertion && i >= insertion.index ? insertion.gap : 0}
                          onRemove={() => removeStep(step.instanceId)}
                        />
                      ))}
                    </SortableContext>
                    {steps.length === 0 && (
                      <Box sx={{ textAlign: 'center', py: 4 }}>
                        <Typography variant="body2" color="text.secondary">
                          Drag actions from the right panel to build your pipeline
                        </Typography>
                      </Box>
                    )}
                  </DroppablePipelineArea>
                </>
              ) : (
                <TextField
                  value={yamlText}
                  onChange={e => { setYamlText(e.target.value); setYamlError(null) }}
                  fullWidth
                  multiline
                  minRows={16}
                  maxRows={36}
                  sx={{
                    '& .MuiInputBase-input': {
                      fontFamily: '"JetBrains Mono", monospace',
                      fontSize: '0.82rem',
                      lineHeight: 1.6,
                    },
                  }}
                  placeholder={`name: My Pipeline\ndescription: Optional description\nsteps:\n  - actionKey: run-updatedb-preprocessor\n  - actionKey: build-ear`}
                />
              )}
            </Paper>
          </Box>

          {/* Right panel — Available Actions (always visible for reference) */}
          <Box sx={{ flex: 2, minWidth: 0 }}>
            <Paper sx={{ p: { xs: 1.5, sm: 2.5 } }}>
              <Typography variant="subtitle2" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.75rem', mb: 2 }}>
                Available Actions
              </Typography>
              {visibleActions.length === 0 && (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    {actions.length === 0
                      ? 'No actions available'
                      : 'No actions in scope. Change scope or create a matching action.'}
                  </Typography>
                </Box>
              )}
              {visibleActions.map(action => (
                <DraggableActionCard key={action.actionKey} action={action} />
              ))}
            </Paper>
          </Box>
        </Box>

        <DragOverlay>
          {activeAction && (
            <Paper
              sx={{
                p: 1.5,
                opacity: 0.9,
                borderColor: 'rgba(99, 102, 241, 0.3)',
                boxShadow: '0 8px 32px rgba(99, 102, 241, 0.15)',
              }}
              variant="outlined"
              elevation={3}
            >
              <Stack direction="row" alignItems="center" spacing={1}>
                <DragIndicatorIcon fontSize="small" sx={{ color: '#6366f1' }} />
                <Typography variant="body2" fontWeight={500}>{activeAction.name}</Typography>
                <Chip
                  label={activeAction.targetRole}
                  size="small"
                  sx={{ fontSize: '0.65rem', height: 20, bgcolor: 'rgba(99,102,241,0.1)', color: '#818cf8', border: 'none' }}
                />
              </Stack>
            </Paper>
          )}
        </DragOverlay>
      </DndContext>
    </>
  )
}
