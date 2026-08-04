import { test, expect, Page, Locator } from '@playwright/test'

/**
 * Dragging an action from the palette into the pipeline opens a gap at the insertion point, so
 * it's obvious where the drop will land. These specs assert the gap and the resulting order
 * together — a preview that disagrees with the drop would be worse than no preview at all.
 *
 * Hovering a step means "before this one", and the space held open below the list means "at the
 * end". One position per step: the list must move exactly once per card the pointer crosses.
 */

const SEED_YAML = [
  'name: e2e-gap-test',
  'steps:',
  '  - actionKey: run-updatedb-preprocessor',
  '  - actionKey: build-ear',
  '  - actionKey: run-updatedb',
  '  - actionKey: mas-fix-pre-updatedb',
  '  - actionKey: start-app',
].join('\n')

const SEEDED_NAMES = [
  'Run UpdateDB Pre-Processor',
  'Build EAR',
  'Run Maximo UpdateDB',
  'MAS pre-updatedb DB fixes',
  'Start APP Container',
]

/** Fills the builder via the YAML editor — faster and less brittle than five drags. */
async function seedSteps(page: Page) {
  await page.goto('/pipelines')
  await expect(page.getByRole('heading', { name: 'Pipelines' })).toBeVisible()

  // "Export YAML" also matches a loose name lookup — the editor toggle is the exact one.
  await page.getByRole('button', { name: 'YAML', exact: true }).click()
  await page.locator('textarea:not([aria-hidden])').first().fill(SEED_YAML)
  await page.getByRole('button', { name: 'Form', exact: true }).click()

  await expect(page.getByTestId('pipeline-step')).toHaveCount(SEEDED_NAMES.length)
}

function stepNames(page: Page) {
  return page.getByTestId('pipeline-step').evaluateAll(
    els => els.map(el => (el as HTMLElement).innerText.split('\n').filter(Boolean)[1]),
  )
}

/** Vertical offset each card is currently displaced by, rounded to whole pixels. */
function stepShifts(page: Page) {
  return page.getByTestId('pipeline-step').evaluateAll(els => els.map(el => {
    const m = new DOMMatrixReadOnly(getComputedStyle(el as HTMLElement).transform)
    return Math.round(m.m42)
  }))
}

async function centre(locator: Locator) {
  const box = await locator.boundingBox()
  if (!box) throw new Error('element has no box')
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

/**
 * Where each step sits before anything is dragged. Read these up front and use them for the whole
 * gesture: once the gap opens the cards are displaced, so a box read mid-drag points at a slot the
 * card has already left — which is not where the pointer thinks it is.
 */
async function stepBoxes(page: Page) {
  const boxes = await page.getByTestId('pipeline-step').evaluateAll(
    els => els.map(el => {
      const r = (el as HTMLElement).getBoundingClientRect()
      return { x: r.x, y: r.y, width: r.width, height: r.height }
    }),
  )
  return boxes
}

/** Picks up a palette card and moves it to `to`, leaving the pointer down. */
async function dragTo(page: Page, actionName: string, to: { x: number; y: number }) {
  const source = page.getByTestId('palette-action').filter({ hasText: actionName }).first()
  const from = await centre(source)

  await page.mouse.move(from.x, from.y)
  await page.mouse.down()
  // dnd-kit activates after 5px and then measures drop targets on an animation frame, so the
  // gesture has to span several frames — one big move in a single task isn't enough.
  for (let i = 1; i <= 10; i++) {
    await page.mouse.move(from.x + (to.x - from.x) * i / 10, from.y + (to.y - from.y) * i / 10)
    await page.waitForTimeout(30)
  }
}

/** A point `yFraction` down a step's original box. */
function pointOn(box: { x: number; y: number; width: number; height: number }, yFraction: number) {
  return { x: box.x + box.width / 2, y: box.y + box.height * yFraction }
}

test.describe('Pipeline builder drop preview', () => {
  // The palette and the step list are side by side and both need to be on screen for a drag —
  // at the default 720px height the palette runs off the bottom.
  test.use({ viewport: { width: 1600, height: 1200 } })

  test('cards below the insertion point make way, and the drop lands in the gap', async ({ page }) => {
    await seedSteps(page)

    const boxes = await stepBoxes(page)
    const gap = boxes[2].height + 8

    await dragTo(page, 'Deploy Package', pointOn(boxes[2], 0.25))

    await expect.poll(() => stepShifts(page)).toEqual([0, 0, gap, gap, gap])

    await page.mouse.up()

    expect(await stepNames(page)).toEqual([
      SEEDED_NAMES[0], SEEDED_NAMES[1], 'Deploy Package', SEEDED_NAMES[2], SEEDED_NAMES[3], SEEDED_NAMES[4],
    ])
  })

  test('the gap does not move while the pointer stays on one card', async ({ page }) => {
    await seedSteps(page)

    const boxes = await stepBoxes(page)
    const gap = boxes[2].height + 8
    const expected = [0, 0, gap, gap, gap]

    await dragTo(page, 'Deploy Package', pointOn(boxes[2], 0.2))
    await expect.poll(() => stepShifts(page)).toEqual(expected)

    // Two things used to make the list twitch here: an insert-before/insert-after split at the
    // card's midpoint, and pointerWithin ranking the drop area above the step whenever the
    // pointer neared the middle of the list, which is roughly where these samples sit.
    for (const fraction of [0.4, 0.5, 0.6, 0.8]) {
      const p = pointOn(boxes[2], fraction)
      await page.mouse.move(p.x, p.y)
      await page.waitForTimeout(40)
      expect(await stepShifts(page)).toEqual(expected)
    }

    await page.mouse.up()
    expect(await stepNames(page)).toEqual([
      SEEDED_NAMES[0], SEEDED_NAMES[1], 'Deploy Package', SEEDED_NAMES[2], SEEDED_NAMES[3], SEEDED_NAMES[4],
    ])
  })

  test('the space below the list appends to the end', async ({ page }) => {
    await seedSteps(page)

    const boxes = await stepBoxes(page)
    const last = boxes[4]
    const gap = last.height + 8

    // Land on the last card first, so the tail space is open, then move down into it.
    await dragTo(page, 'Deploy Package', pointOn(last, 0.5))
    await expect.poll(() => stepShifts(page)).toEqual([0, 0, 0, 0, gap])

    await page.mouse.move(last.x + last.width / 2, last.y + last.height + 20)
    await expect.poll(() => stepShifts(page)).toEqual([0, 0, 0, 0, 0])

    await page.mouse.up()
    expect(await stepNames(page)).toEqual([...SEEDED_NAMES, 'Deploy Package'])
  })

  test('the gap closes when the pointer leaves the pipeline', async ({ page }) => {
    await seedSteps(page)

    const boxes = await stepBoxes(page)

    await dragTo(page, 'Deploy Package', pointOn(boxes[2], 0.25))
    await expect.poll(() => stepShifts(page)).not.toEqual([0, 0, 0, 0, 0])

    // Back out over the palette — nothing is pending, so nothing should be displaced.
    const palette = page.getByTestId('palette-action').first()
    const over = await centre(palette)
    await page.mouse.move(over.x, over.y, { steps: 12 })
    await expect.poll(() => stepShifts(page)).toEqual([0, 0, 0, 0, 0])

    await page.mouse.up()
    expect(await stepNames(page)).toEqual(SEEDED_NAMES)
  })
})
