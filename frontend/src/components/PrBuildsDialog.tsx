import { useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, Box, Typography, Chip, IconButton, Collapse,
  Table, TableBody, TableCell, TableHead, TableRow, Skeleton, Alert,
} from '@mui/material'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
import OpenInNewIcon from '@mui/icons-material/OpenInNewRounded'
import { useQuery } from '@tanstack/react-query'
import { getPRBuilds, PRBuild } from '../api/client'
import ContainerStatusBadge from './ContainerStatusBadge'
import LogViewer from './LogViewer'
import { usePrBuildLogStream } from '../hooks/usePrBuildLogStream'

const ACTIVE = new Set(['QUEUED', 'CLONING', 'BUILDING'])

function PrBuildRow({ build, expanded, onToggle }: {
  build: PRBuild
  expanded: boolean
  onToggle: () => void
}) {
  // Stream live logs only for an expanded, in-flight build.
  const { lines } = usePrBuildLogStream(expanded && ACTIVE.has(build.status) ? build.id : null)

  return (
    <>
      <TableRow hover sx={{ cursor: 'pointer', '& > *': { borderBottom: expanded ? 'unset' : undefined } }} onClick={onToggle}>
        <TableCell sx={{ width: 40 }}>
          <IconButton size="small" sx={{ color: '#64748b' }}>
            {expanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Typography variant="body2" fontWeight={500}>#{build.prNumber}{build.prTitle ? `: ${build.prTitle}` : ''}</Typography>
          <Typography variant="caption" sx={{ color: '#64748b', fontFamily: '"JetBrains Mono", monospace', fontSize: '0.68rem' }}>
            {build.sourceBranch}{build.commitSha ? ` @ ${build.commitSha.slice(0, 8)}` : ''}
          </Typography>
        </TableCell>
        <TableCell><ContainerStatusBadge status={build.status} /></TableCell>
        <TableCell>
          {build.environmentId != null && (
            <Chip
              size="small" variant="outlined" icon={<OpenInNewIcon sx={{ fontSize: '14px !important' }} />}
              label={`env ${build.environmentId}`} component="a" clickable
              href={`/environments/${build.environmentId}`} onClick={e => e.stopPropagation()}
              sx={{ height: 22, fontSize: '0.65rem' }}
            />
          )}
        </TableCell>
        <TableCell>
          <Typography variant="caption" color="text.secondary">
            {build.createdAt ? new Date(build.createdAt).toLocaleString() : '-'}
          </Typography>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={5}>
          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <Box sx={{ py: 1.5 }}>
              {build.error && <Alert severity="error" sx={{ mb: 1 }}>{build.error}</Alert>}
              {ACTIVE.has(build.status) ? (
                <LogViewer lines={lines} filename={`pr-${build.prNumber}-build`} />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {build.status === 'SUCCESS'
                    ? 'Build succeeded. Live logs are only available while a build runs.'
                    : 'Live logs are only available while a build runs.'}
                </Typography>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  )
}

export default function PrBuildsDialog({ open, onClose, repoId, repoName }: {
  open: boolean
  onClose: () => void
  repoId: number | null
  repoName: string
}) {
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const { data: builds = [], isLoading } = useQuery({
    queryKey: ['prBuilds', repoId],
    queryFn: () => getPRBuilds(repoId as number),
    enabled: open && repoId != null,
    refetchInterval: open ? 4000 : false,
  })

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>PR Builds — {repoName}</DialogTitle>
      <DialogContent>
        {isLoading && <Skeleton variant="rounded" height={180} />}
        {!isLoading && builds.length === 0 && (
          <Box sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
            <Typography variant="body2">No PR builds yet. Open or update a PR to trigger one.</Typography>
          </Box>
        )}
        {!isLoading && builds.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell />
                <TableCell>Pull request</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Environment</TableCell>
                <TableCell>Created</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {builds.map(b => (
                <PrBuildRow key={b.id} build={b} expanded={expandedId === b.id}
                  onToggle={() => setExpandedId(expandedId === b.id ? null : b.id)} />
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  )
}
