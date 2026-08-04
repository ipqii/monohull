import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography, useMediaQuery, useTheme } from '@mui/material'
import { useEffect, useRef, useState } from 'react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import ContainerStatusBadge from './ContainerStatusBadge'

interface Props {
  open: boolean
  onClose: () => void
  containerId: number | null
  containerName: string
}

type ConnectionState = 'connecting' | 'connected' | 'closed'

const badgeStatus: Record<ConnectionState, string> = {
  connecting: 'PENDING',
  connected: 'RUNNING',
  closed: 'STOPPED',
}

/**
 * Interactive shell into a container: xterm.js in the browser, bridged over a
 * websocket to `docker exec` on the backend. Binary frames carry the byte
 * streams both ways; resize events go as JSON text frames.
 */
export default function ContainerTerminalDialog({ open, onClose, containerId, containerName }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const termRef = useRef<HTMLDivElement>(null)
  const [connection, setConnection] = useState<ConnectionState>('connecting')

  useEffect(() => {
    if (!open || containerId === null) return
    const el = termRef.current
    if (!el) return

    setConnection('connecting')

    const term = new Terminal({
      cursorBlink: true,
      fontFamily: '"JetBrains Mono", monospace',
      fontSize: 13,
      lineHeight: 1.25,
      scrollback: 5000,
      theme: {
        background: '#020617',
        foreground: '#94a3b8',
        cursor: '#22d3ee',
        cursorAccent: '#020617',
        selectionBackground: 'rgba(34, 211, 238, 0.25)',
      },
    })
    const fit = new FitAddon()
    term.loadAddon(fit)
    term.open(el)
    fit.fit()

    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    let ws: WebSocket | null = null
    try {
      ws = new WebSocket(`${proto}://${window.location.host}/api/containers/${containerId}/terminal`)
    } catch (err) {
      // A synchronous throw here is almost always the page's CSP refusing the ws://
      // scheme — surface it instead of sitting on "connecting" forever.
      setConnection('closed')
      term.write(`\x1b[38;5;203m[websocket refused before connecting: ${err instanceof Error ? err.message : String(err)}]\x1b[0m\r\n`)
    }
    const encoder = new TextEncoder()

    const socket = ws
    const sendResize = () => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }))
      }
    }

    if (socket) {
      socket.binaryType = 'arraybuffer'
      socket.onopen = () => {
        setConnection('connected')
        sendResize()
        term.focus()
      }
      socket.onmessage = e => {
        term.write(new Uint8Array(e.data as ArrayBuffer))
      }
      socket.onclose = e => {
        setConnection('closed')
        const reason = e.reason || (e.wasClean ? 'session ended' : 'connection lost')
        term.write(`\r\n\x1b[38;5;240m[${reason}]\x1b[0m\r\n`)
      }
    }

    const dataSub = term.onData(data => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(encoder.encode(data))
      }
    })
    const resizeSub = term.onResize(() => sendResize())

    // Refit whenever the dialog body changes size (open animation, window resize,
    // fullscreen toggle) — xterm needs explicit refits, it doesn't observe on its own.
    const observer = new ResizeObserver(() => fit.fit())
    observer.observe(el)

    return () => {
      observer.disconnect()
      dataSub.dispose()
      resizeSub.dispose()
      socket?.close()
      term.dispose()
    }
  }, [open, containerId])

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="lg"
      fullWidth
      fullScreen={fullScreen}
      PaperProps={{ sx: { height: fullScreen ? '100vh' : '80vh', display: 'flex', flexDirection: 'column' } }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, fontWeight: 600 }}>
        Terminal — {containerName}
        <ContainerStatusBadge status={badgeStatus[connection]} />
      </DialogTitle>
      <DialogContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            borderRadius: 3,
            overflow: 'hidden',
            border: '1px solid rgba(34, 211, 238, 0.08)',
            bgcolor: '#020617',
          }}
        >
          {/* Terminal chrome, matching LogViewer */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              px: 2,
              py: 1,
              borderBottom: '1px solid rgba(34, 211, 238, 0.06)',
              bgcolor: 'rgba(2, 6, 23, 0.8)',
              flexShrink: 0,
            }}
          >
            <Box sx={{ display: 'flex', gap: 0.75, mr: 2 }}>
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ef4444', opacity: 0.8 }} />
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#f59e0b', opacity: 0.8 }} />
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#22c55e', opacity: 0.8 }} />
            </Box>
            <Typography
              variant="caption"
              sx={{ flex: 1, color: '#475569', fontFamily: '"JetBrains Mono", monospace', fontSize: '0.7rem' }}
            >
              {containerName}
            </Typography>
          </Box>
          <Box ref={termRef} sx={{ flex: 1, minHeight: 0, px: 1.5, py: 1, '& .xterm': { height: '100%' } }} />
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} variant="outlined">Close</Button>
      </DialogActions>
    </Dialog>
  )
}
