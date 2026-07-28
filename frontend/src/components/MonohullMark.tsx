/**
 * The Monohull brand mark: a sloop heeling on a level horizon, seen from ahead.
 *
 * Geometry is the supplied brand pack verbatim (docs/brand/sailboat-mark-white.svg) on its
 * 200x200 artboard, with the pack's tonal split reproduced through fill-opacity rather than
 * baked-in whites — so the mark takes whatever colour it is given and works on the gradient
 * tile, on the dark app bar, and anywhere else.
 *
 * The tile itself is not part of this component. Callers that want one draw it around the
 * mark (see the sidebar and login lockups), which keeps this usable as a bare glyph.
 */
type Props = {
  /** Rendered width and height in px. The mark very nearly fills it — see VIEW_BOX. */
  size?: number
  /** Any CSS colour; the mark's mast and hull take it solid, the sails at 66% and 34%. */
  color?: string
}

/**
 * Square window cropped to the mark's own bounds rather than the pack's 200x200 artboard.
 * Once heeled, the artwork occupies roughly x 63..138, y 32..173, so the artboard leaves a
 * third of its width empty — rendering it whole makes the mark look shrunken inside the
 * brand tile. This box is that content area squared off with a little breathing room, so
 * `size` is the size of the boat rather than the size of the padding around it.
 */
const VIEW_BOX = '26 28 149 149'

export default function MonohullMark({ size = 22, color = 'currentColor' }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={VIEW_BOX}
      style={{ color, display: 'block' }}
      role="img"
      aria-label="Monohull"
    >
      <g transform="translate(-7 -4) rotate(18 100 162)">
        <path d="M104 30 C111 66 118 100 128 132 L104 130 Z" fill="currentColor" fillOpacity={0.66} />
        <path d="M97 50 C88 84 81 110 72 138 L97 130 Z" fill="currentColor" fillOpacity={0.34} />
        <rect x="99" y="30" width="4" height="106" fill="currentColor" />
        <path
          d="M62 138 C74 129 126 129 138 138 C128 158 115 169 100 172 C85 169 72 158 62 138 Z"
          fill="currentColor"
        />
      </g>
    </svg>
  )
}
