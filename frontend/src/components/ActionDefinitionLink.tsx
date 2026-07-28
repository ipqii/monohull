import { IconButton, Tooltip } from '@mui/material'
import OpenInNewIcon from '@mui/icons-material/OpenInNewRounded'

/**
 * Jumps from an action referenced in a pipeline to its definition.
 *
 * Opens in a new tab on purpose: the pipeline builder holds unsaved steps in local state, so
 * navigating away in-place would silently discard them, and having the definition open beside
 * the pipeline is the point of the shortcut.
 *
 * `actionId` is the numeric custom_action id — null for an action key that no longer resolves
 * (deleted action, or a key typed into the YAML editor), in which case nothing is rendered.
 */
export default function ActionDefinitionLink({ actionId }: { actionId: number | null | undefined }) {
  if (actionId == null) return null

  return (
    <Tooltip title="Open action definition in a new tab">
      <IconButton
        component="a"
        href={`/config/actions/${actionId}/edit`}
        target="_blank"
        rel="noopener"
        size="small"
        aria-label="Open action definition in a new tab"
        // These cards sit inside dnd-kit drag listeners; without stopping the pointer event the
        // press that follows the link also arms the drag sensor.
        onPointerDown={e => e.stopPropagation()}
        onClick={e => e.stopPropagation()}
        sx={{ color: '#475569', flexShrink: 0, '&:hover': { color: '#818cf8' } }}
      >
        <OpenInNewIcon sx={{ fontSize: 16 }} />
      </IconButton>
    </Tooltip>
  )
}
