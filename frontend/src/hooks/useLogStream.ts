import { useEffect, useRef, useState } from 'react'

/**
 * Live build log for an environment.
 *
 * The server's live stream doesn't replay what was logged before the client connected, so a
 * caller that also shows saved history passes `afterId`: the id of the last saved line it
 * already has. The server then sends every saved line after it before switching to live
 * output, so history + these lines is the whole log, without gaps or duplicates. The stream
 * only opens once `afterId` is known (pass null to wait); undefined streams live lines only.
 */
export function useLogStream(environmentId: number | null, buildId: string | null, afterId?: number | null) {
  const [lines, setLines] = useState<string[]>([])
  const [connected, setConnected] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)
  // Coalesce SSE events into one setState per animation frame. `unzip` output can
  // arrive at hundreds of lines/sec; per-event setState makes React reconcile the
  // entire log list on each line, dropping the UI to a crawl. Batching means the
  // UI updates ~60Hz regardless of event arrival rate.
  const pendingRef = useRef<string[]>([])
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    if (!environmentId || !buildId || afterId === null) return

    const flush = () => {
      rafRef.current = null
      if (pendingRef.current.length === 0) return
      const batch = pendingRef.current
      pendingRef.current = []
      setLines(prev => prev.concat(batch))
    }

    // On an automatic reconnect the browser sends Last-Event-ID, so the server resumes after
    // the last line received rather than skipping what was logged while disconnected.
    const query = afterId !== undefined ? `?after=${afterId}` : ''
    const es = new EventSource(`/api/environments/${environmentId}/logs${query}`)
    eventSourceRef.current = es
    // EventSource reconnects automatically after a dropped connection — never
    // close it on the first error, or one proxy blip / server restart kills the
    // live log until a full page reload. Only give up after several errors in a
    // row with no data in between, which is what a finished build looks like
    // (the server completes the stream immediately on every reconnect).
    let consecutiveErrors = 0

    es.onopen = () => setConnected(true)

    es.onmessage = (event) => {
      consecutiveErrors = 0
      pendingRef.current.push(event.data)
      if (rafRef.current === null) {
        rafRef.current = requestAnimationFrame(flush)
      }
    }

    es.onerror = () => {
      setConnected(false)
      consecutiveErrors += 1
      if (consecutiveErrors >= 5) {
        es.close()
      }
    }

    return () => {
      es.close()
      setConnected(false)
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      pendingRef.current = []
      setLines([])
    }
  }, [environmentId, buildId, afterId])

  return { lines, connected }
}
