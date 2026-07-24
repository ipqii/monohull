import { useEffect, useRef, useState } from 'react'

export function useLogStream(environmentId: number | null, buildId: string | null) {
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
    if (!environmentId || !buildId) return

    const flush = () => {
      rafRef.current = null
      if (pendingRef.current.length === 0) return
      const batch = pendingRef.current
      pendingRef.current = []
      setLines(prev => prev.concat(batch))
    }

    const es = new EventSource(`/api/environments/${environmentId}/logs`)
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
    }
  }, [environmentId, buildId])

  return { lines, connected }
}
