import { useEffect, useRef, useState } from 'react'

export function useActionLogStream(executionId: string | null) {
  const [lines, setLines] = useState<string[]>([])
  const [connected, setConnected] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!executionId) {
      setLines([])
      return
    }

    const es = new EventSource(`/api/actions/executions/${executionId}/logs`)
    eventSourceRef.current = es
    // See useLogStream: rely on EventSource's automatic reconnection and only
    // give up after repeated immediate failures (= the execution has finished).
    let consecutiveErrors = 0

    es.onopen = () => setConnected(true)

    es.onmessage = (event) => {
      consecutiveErrors = 0
      setLines(prev => [...prev, event.data])
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
    }
  }, [executionId])

  return { lines, connected }
}
