import { IconButton, Tooltip } from '@mui/material'
import OpenInNewIcon from '@mui/icons-material/OpenInNewRounded'

/**
 * Jumps from a pipeline selected in a form to its definition on the Pipelines page.
 *
 * Opens in a new tab, like {@link ActionDefinitionLink}: the forms this sits in hold unsaved
 * edits, and having the pipeline open beside the form is the point of the shortcut.
 * Renders nothing when no pipeline is selected.
 */
export default function PipelineDefinitionLink({ pipelineId }: { pipelineId: number | null | undefined }) {
  if (pipelineId == null) return null

  return (
    <Tooltip title="Open pipeline definition in a new tab">
      <IconButton
        component="a"
        href={`/pipelines?id=${pipelineId}`}
        target="_blank"
        rel="noopener"
        size="small"
        aria-label="Open pipeline definition in a new tab"
        sx={{ color: '#475569', flexShrink: 0, '&:hover': { color: '#818cf8' } }}
      >
        <OpenInNewIcon sx={{ fontSize: 16 }} />
      </IconButton>
    </Tooltip>
  )
}
