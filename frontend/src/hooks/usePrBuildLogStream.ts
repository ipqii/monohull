import { useEffect, useRef, useState } from 'react'

/** Live SSE stream of a PR build's log output. Returns accumulated lines + connection state.
 *  Only carries data while the build is in-flight (the stream is in-memory server-side). */
export function usePrBuildLogStream(prBuildId: number | null) {
  const [lines, setLines] = useState<string[]>([])
  const [connected, setConnected] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!prBuildId) {
      setLines([])
      return
    }

    const es = new EventSource(`/api/config/repositories/pr-builds/${prBuildId}/logs`)
    eventSourceRef.current = es

    es.onopen = () => setConnected(true)
    es.onmessage = (event) => setLines(prev => [...prev, event.data])
    es.onerror = () => { setConnected(false); es.close() }

    return () => { es.close(); setConnected(false) }
  }, [prBuildId])

  return { lines, connected }
}
