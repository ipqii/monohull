// navigator.clipboard exists only in secure contexts (HTTPS or localhost). Monohull is
// commonly served over plain HTTP on a LAN host, where it is undefined, so every copy
// goes through here and falls back to a hidden textarea + execCommand.

function legacyCopy(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '0'
  textarea.style.left = '0'
  textarea.style.opacity = '0'
  // Inside an open MUI Dialog the focus trap pulls focus back from anything appended
  // to <body>, which empties the selection before the copy; stay inside the dialog.
  const active = document.activeElement
  const host = (active instanceof HTMLElement && active.closest('[role="dialog"]')) || document.body
  host.appendChild(textarea)
  try {
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, text.length)
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    host.removeChild(textarea)
    if (active instanceof HTMLElement) active.focus()
  }
}

/** Copies text to the clipboard; resolves to whether it was copied. Never rejects. */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // Denied or unavailable (permissions policy, unfocused document): try the fallback.
    }
  }
  return legacyCopy(text)
}
