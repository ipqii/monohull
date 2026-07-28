import { test, expect, Page, Locator } from '@playwright/test'

/**
 * Dragging an action from the palette into the pipeline opens a gap at the insertion point, so
 * it's obvious where the drop will land. These specs assert the gap and the resulting order
 * together — a preview that disagrees with the drop would be worse than no preview at all.
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
 * Picks up a palette card and moves it over `target`, `yFraction` down that card's height.
 * Leaves the pointer down so the caller can inspect the gap before dropping.
 */
async function dragOver(page: Page, actionName: string, target: Locator, yFraction: number) {
  const source = page.getByTestId('palette-action').filter({ hasText: actionName }).first()
  const from = await centre(source)
  const box = await target.boundingBox()
  if (!box) throw new Error('target has no box')

  const to = { x: box.x + box.width / 2, y: box.y + box.height * yFraction }

  await page.mouse.move(from.x, from.y)
  await page.mouse.down()
  // dnd-kit activates after 5px and then measures drop targets on an animation frame, so the
  // gesture has to span several frames — one big move in a single task isn't enough.
  for (let i = 1; i <= 10; i++) {
    await page.mouse.move(from.x + (to.x - from.x) * i / 10, from.y + (to.y - from.y) * i / 10)
    await page.waitForTimeout(30)
  }
}

test.describe('Pipeline builder drop preview', () => {
  // The palette and the step list are side by side and both need to be on screen for a drag —
  // at the default 720px height the palette runs off the bottom.
  test.use({ viewport: { width: 1600, height: 1200 } })

  test('cards below the insertion point make way, and the drop lands in the gap', async ({ page }) => {
    await seedSteps(page)

    const steps = page.getByTestId('pipeline-step')
    const thirdCard = steps.nth(2)
    const cardHeight = (await thirdCard.boundingBox())!.height

    // Upper half of the third card => insert before it.
    await dragOver(page, 'Deploy Package', thirdCard, 0.25)

    await expect.poll(() => stepShifts(page)).toEqual([
      0, 0, cardHeight + 8, cardHeight + 8, cardHeight + 8,
    ])

    await page.mouse.up()

    expect(await stepNames(page)).toEqual([
      SEEDED_NAMES[0], SEEDED_NAMES[1], 'Deploy Package', SEEDED_NAMES[2], SEEDED_NAMES[3], SEEDED_NAMES[4],
    ])
  })

  test('hovering the lower half of a card inserts after it', async ({ page }) => {
    await seedSteps(page)

    const steps = page.getByTestId('pipeline-step')
    const thirdCard = steps.nth(2)
    const cardHeight = (await thirdCard.boundingBox())!.height

    await dragOver(page, 'Deploy Package', thirdCard, 0.75)

    // The hovered card stays put; only the two below it move.
    await expect.poll(() => stepShifts(page)).toEqual([
      0, 0, 0, cardHeight + 8, cardHeight + 8,
    ])

    await page.mouse.up()

    expect(await stepNames(page)).toEqual([
      SEEDED_NAMES[0], SEEDED_NAMES[1], SEEDED_NAMES[2], 'Deploy Package', SEEDED_NAMES[3], SEEDED_NAMES[4],
    ])
  })

  test('the gap closes when the pointer leaves the pipeline', async ({ page }) => {
    await seedSteps(page)

    const steps = page.getByTestId('pipeline-step')
    const thirdCard = steps.nth(2)

    await dragOver(page, 'Deploy Package', thirdCard, 0.25)
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
