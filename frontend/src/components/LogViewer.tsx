import { Box, Typography, IconButton, Tooltip } from '@mui/material'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DownloadIcon from '@mui/icons-material/Download'
import CheckIcon from '@mui/icons-material/Check'
import PauseIcon from '@mui/icons-material/Pause'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { useEffect, useRef, useState, useCallback, useMemo } from 'react'

function fallbackCopy(text: string) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

interface Props {
  lines: string[]
  filename?: string
}

const ROW_HEIGHT = 22         // px — kept in sync with fontSize 0.8rem × lineHeight 1.65
const PADDING_TOP = 12        // matches py: 1.5 (~12px)
const OVERSCAN = 20           // extra rows above/below the viewport
const LINE_NUMBER_WIDTH = 56  // includes left padding + number column + right gap

export default function LogViewer({ lines, filename }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const [copied, setCopied] = useState(false)
  // "follow" = auto-scroll to bottom as new lines arrive. We mirror it into a ref so the
  // scroll handler reads the live value without being re-created, and so the auto-scroll
  // effect doesn't need it as a dependency.
  const [follow, setFollow] = useState(true)
  const followRef = useRef(true)
  const setFollowing = useCallback((v: boolean) => {
    followRef.current = v
    setFollow(v)
  }, [])
  // Programmatic scroll-to-bottom fires the same 'scroll' event as a user drag, and during
  // a burst those events arrive AFTER more content has grown the height — so a naive
  // position check would read "scrolled up" and wrongly pause following. We therefore only
  // ever turn following OFF in response to a real user gesture (wheel/touch/scrollbar drag/
  // keyboard), tracked here; position alone only ever turns it back ON (reaching the bottom).
  const pointerDownRef = useRef(false)
  const lastGestureRef = useRef(0)
  const [scrollTop, setScrollTop] = useState(0)
  const [viewportHeight, setViewportHeight] = useState(0)

  // Many backend "lines" are actually multi-line strings — Docker exec frames batch output and
  // Maximo emits whole XML payloads in one shot. Virtualization relies on a fixed row height,
  // so if a row contains embedded newlines its content overflows down and overlaps the next
  // row. Split here so every visual row is exactly one physical line.
  const visualLines = useMemo(() => {
    const out: string[] = []
    for (const raw of lines) {
      if (raw.indexOf('\n') < 0) {
        out.push(raw)
      } else {
        for (const part of raw.split('\n')) out.push(part)
      }
    }
    return out
  }, [lines])

  // Track viewport size so the window of rendered rows recomputes on resize.
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const ro = new ResizeObserver(() => setViewportHeight(el.clientHeight))
    ro.observe(el)
    setViewportHeight(el.clientHeight)
    return () => ro.disconnect()
  }, [])

  // Auto-scroll to bottom when new lines arrive, while following.
  useEffect(() => {
    if (!followRef.current) return
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [visualLines.length])

  // Mark that the most recent scroll is user-driven, so handleScroll may pause following.
  // A wheel/touch/key gesture counts for a short window; a pointer (scrollbar drag) counts
  // until release. Programmatic scroll-to-bottom matches neither, so it can never pause.
  const markGesture = useCallback(() => { lastGestureRef.current = Date.now() }, [])
  useEffect(() => {
    const up = () => { pointerDownRef.current = false }
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', up)
    return () => {
      window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', up)
    }
  }, [])

  const handleScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    setScrollTop(el.scrollTop)
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    const atBottom = distanceFromBottom <= ROW_HEIGHT
    if (atBottom) {
      // Reaching the bottom (re)engages following regardless of how we got there.
      if (!followRef.current) setFollowing(true)
    } else if (followRef.current) {
      // Not at the bottom — only break following if the user actually drove this scroll.
      const userDriven = pointerDownRef.current || Date.now() - lastGestureRef.current < 250
      if (userDriven) setFollowing(false)
    }
  }, [setFollowing])

  const totalHeight = visualLines.length * ROW_HEIGHT + PADDING_TOP * 2

  const { startIdx, endIdx } = useMemo(() => {
    if (visualLines.length === 0 || viewportHeight === 0) {
      return { startIdx: 0, endIdx: 0 }
    }
    const first = Math.max(0, Math.floor((scrollTop - PADDING_TOP) / ROW_HEIGHT) - OVERSCAN)
    const visibleCount = Math.ceil(viewportHeight / ROW_HEIGHT)
    const last = Math.min(visualLines.length, first + visibleCount + OVERSCAN * 2)
    return { startIdx: first, endIdx: last }
  }, [scrollTop, viewportHeight, visualLines.length])

  const visibleRows: { index: number; line: string }[] = []
  for (let i = startIdx; i < endIdx; i++) {
    visibleRows.push({ index: i, line: visualLines[i] })
  }

  const lineNumberDigits = Math.max(2, String(visualLines.length).length)

  const getLogText = useCallback(() => visualLines.join('\n'), [visualLines])

  const handleCopy = useCallback(() => {
    const text = getLogText()
    const done = () => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(() => {
        fallbackCopy(text)
        done()
      })
    } else {
      fallbackCopy(text)
      done()
    }
  }, [getLogText])

  const handleDownload = useCallback(() => {
    const blob = new Blob([getLogText()], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = (filename || 'build-log') + '.log'
    a.click()
    URL.revokeObjectURL(url)
  }, [getLogText, filename])

  return (
    <Box
      sx={{
        borderRadius: 3,
        overflow: 'hidden',
        border: '1px solid rgba(34, 211, 238, 0.08)',
        bgcolor: '#020617',
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minHeight: 0,
      }}
    >
      {/* Terminal header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          py: 1,
          borderBottom: '1px solid rgba(34, 211, 238, 0.06)',
          bgcolor: 'rgba(2, 6, 23, 0.8)',
        }}
      >
        <Box sx={{ display: 'flex', gap: 0.75, mr: 2 }}>
          <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ef4444', opacity: 0.8 }} />
          <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#f59e0b', opacity: 0.8 }} />
          <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#22c55e', opacity: 0.8 }} />
        </Box>
        <Typography
          variant="caption"
          sx={{
            flex: 1,
            color: '#475569',
            fontFamily: '"JetBrains Mono", monospace',
            fontSize: '0.7rem',
          }}
        >
          {filename || 'terminal'}
        </Typography>
        {visualLines.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.25 }}>
            <Tooltip title={follow ? 'Pause auto-scroll' : 'Resume auto-scroll'} placement="top">
              <IconButton
                size="small"
                onClick={() => {
                  if (follow) {
                    setFollowing(false)
                  } else {
                    setFollowing(true)
                    const el = scrollRef.current
                    if (el) el.scrollTop = el.scrollHeight
                  }
                }}
                sx={{
                  color: follow ? '#475569' : '#fbbf24',
                  '&:hover': { color: follow ? '#94a3b8' : '#fcd34d' },
                }}
              >
                {follow ? <PauseIcon sx={{ fontSize: 15 }} /> : <PlayArrowIcon sx={{ fontSize: 15 }} />}
              </IconButton>
            </Tooltip>
            <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'} placement="top">
              <IconButton size="small" onClick={handleCopy} sx={{ color: '#475569', '&:hover': { color: '#94a3b8' } }}>
                {copied ? <CheckIcon sx={{ fontSize: 15 }} /> : <ContentCopyIcon sx={{ fontSize: 15 }} />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Download log" placement="top">
              <IconButton size="small" onClick={handleDownload} sx={{ color: '#475569', '&:hover': { color: '#94a3b8' } }}>
                <DownloadIcon sx={{ fontSize: 15 }} />
              </IconButton>
            </Tooltip>
          </Box>
        )}
      </Box>

      {/* Log content — virtualized: only the rows in view (plus an overscan buffer) are mounted,
          which keeps the DOM small even with millions of lines. Rows are absolutely positioned
          inside a tall spacer so the native scrollbar reflects total length. */}
      <Box
        ref={scrollRef}
        onScroll={handleScroll}
        onWheel={markGesture}
        onTouchStart={markGesture}
        onTouchMove={markGesture}
        onKeyDown={markGesture}
        onPointerDown={() => { pointerDownRef.current = true }}
        sx={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
          fontFamily: '"JetBrains Mono", monospace',
          fontSize: '0.8rem',
          lineHeight: `${ROW_HEIGHT}px`,
        }}
      >
        {visualLines.length === 0 ? (
          <Typography
            variant="body2"
            sx={{
              color: '#334155',
              fontFamily: 'inherit',
              fontSize: 'inherit',
              px: 2,
              py: 2,
            }}
          >
            Waiting for output...
          </Typography>
        ) : (
          <Box sx={{ position: 'relative', height: totalHeight, minWidth: 'max-content' }}>
            {visibleRows.map(({ index, line }) => (
              <Box
                key={index}
                sx={{
                  position: 'absolute',
                  top: index * ROW_HEIGHT + PADDING_TOP,
                  left: 0,
                  right: 0,
                  height: ROW_HEIGHT,
                  display: 'flex',
                  whiteSpace: 'pre',
                  '&:hover': { bgcolor: 'rgba(34, 211, 238, 0.03)' },
                }}
              >
                <Box
                  component="span"
                  sx={{
                    color: '#334155',
                    minWidth: LINE_NUMBER_WIDTH,
                    textAlign: 'right',
                    pr: 2,
                    userSelect: 'none',
                    flexShrink: 0,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {String(index + 1).padStart(lineNumberDigits, ' ')}
                </Box>
                <Box
                  component="span"
                  sx={{
                    color: line.includes('[error]') || line.includes('ERROR')
                      ? '#f87171'
                      : line.includes('[warn]') || line.includes('WARN')
                      ? '#fbbf24'
                      : '#94a3b8',
                  }}
                >
                  {line}
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  )
}
