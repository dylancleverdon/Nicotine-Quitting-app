import { expect, test } from '@playwright/test'

// Onboarding with a back-dated week -> starting tier offered -> log a dose -> reload keeps it -> export.
test('back-dated start, logging and persistence', async ({ page }) => {
  // Midday, so "today" is the waking day that started this morning (after midnight it's still yesterday).
  const noon = new Date(); noon.setHours(13, 0, 0, 0)
  await page.clock.install({ time: noon })
  await page.goto('./')
  // Welcome tour first (skippable).
  await expect(page.getByText('Take control of your nicotine')).toBeVisible()
  await page.getByRole('button', { name: 'Skip' }).click()
  await expect(page.getByText('by Baastik Labs').first()).toBeVisible()
  for (let i = 0; i < 3; i++) await page.getByRole('button', { name: 'Next' }).click()
  await page.getByText('Estimate my last week').click()
  await page.getByRole('button', { name: 'Estimate my week' }).click()

  // Day 1: 5 × Zyn 3 mg (≈ 3 pieces), then "same as the day before" for the rest.
  for (let i = 0; i < 5; i++) await page.getByText('Zyn 3 mg', { exact: true }).click()
  await expect(page.getByText('× 5')).toBeVisible()
  await page.getByRole('button', { name: 'Next' }).click()
  for (let d = 1; d < 7; d++) {
    await page.getByRole('button', { name: 'Same as the day before' }).click()
    await page.getByRole('button', { name: d < 6 ? 'Next' : 'Finish' }).click()
  }

  // Tier revealed straight away: 3 pieces a day is Flicker.
  await expect(page.getByText('Your starting point: Flicker')).toBeVisible()
  await page.getByRole('button', { name: 'Start here' }).click()
  await expect(page.locator('.tier')).toHaveText('Flicker')
  await expect(page.getByText(/Stretch .* · Pull .* · Net/)).toBeVisible()
  // Dose preview under the product buttons.
  await expect(page.locator('.product .preview').first()).toBeVisible()
  // One-time net explainer.
  await expect(page.getByText('This is your net')).toBeVisible()
  await page.getByRole('button', { name: 'Got it' }).click()
  await expect(page.getByText('This is your net')).toHaveCount(0)

  // One tap logs a dose.
  await page.locator('.product', { hasText: 'Nicotine gum 4 mg' }).click()
  await expect(page.getByText('Logged Nicotine gum 4 mg')).toBeVisible()
  await expect(page.locator('.list .item')).toHaveCount(1)
  await expect(page.getByText('in your system now')).toBeVisible()
  await page.screenshot({ path: 'test-results/home.png', fullPage: true })

  // Suggestions box opens; cancel without sending anything.
  await page.getByRole('button', { name: 'Suggest something / report a bug' }).click()
  await expect(page.getByPlaceholder('Your suggestion')).toBeVisible()
  await page.screenshot({ path: 'test-results/suggest.png' })
  await page.getByRole('button', { name: 'Cancel' }).click()

  // Relapse prevention mode: on, indicator, next scheduled piece, off.
  await page.getByRole('button', { name: 'Relapse prevention mode', exact: true }).click()
  await page.getByRole('button', { name: 'Turn on' }).click()
  await expect(page.getByText('Relapse prevention mode is on')).toBeVisible()
  await expect(page.getByText(/Next scheduled piece at/).first()).toBeVisible()

  // Help is searchable.
  await page.getByRole('button', { name: 'Help' }).click()
  await page.getByPlaceholder('Search help').fill('friend')
  await expect(page.getByText("Log a friend's vape")).toBeVisible()
  await page.getByRole('button', { name: 'Log' }).click()

  // Survives a reload (IndexedDB).
  await page.reload()
  await expect(page.locator('.list .item')).toHaveCount(1)
  await expect(page.locator('.tier')).toHaveText('Flicker')

  // Insights and calendar render.
  await page.getByRole('button', { name: 'Insights' }).click()
  await expect(page.getByText('Blood-level wave')).toBeVisible()
  await expect(page.getByText('Yesterday in review')).toBeVisible()
  await expect(page.getByText('Nicotine volatility')).toBeVisible()
  // The volatility line over the wave is off until turned on.
  await expect(page.getByText('Volatility (mg), right scale')).toHaveCount(0)
  await page.getByText('Show volatility').click()
  await expect(page.getByText('Volatility (mg), right scale')).toBeVisible()
  await page.screenshot({ path: 'test-results/today.png', fullPage: true })
  // Day charts: step back to yesterday (no overlay).
  await page.locator('.day-stepper button').first().click()
  await expect(page.locator('.day-stepper b')).toHaveText('Yesterday')
  await page.screenshot({ path: 'test-results/yesterday.png', fullPage: true })
  for (const s of ['Trends', 'Patterns', 'Going down']) {
    await page.getByRole('button', { name: s, exact: true }).click()
    await page.screenshot({ path: `test-results/${s.replace(' ', '-').toLowerCase()}.png`, fullPage: true })
  }
  await page.getByRole('button', { name: 'Cravings ahead' }).click()
  await expect(page.getByText(/Next craving likely around|No clear craving peak/)).toBeVisible()
  await page.screenshot({ path: 'test-results/cravings-ahead.png', fullPage: true })
  await page.getByRole('button', { name: 'Receptors' }).click()
  await expect(page.getByText('load today')).toBeVisible()
  await page.screenshot({ path: 'test-results/receptors.png', fullPage: true })
  await page.getByRole('button', { name: 'Stretch & pull' }).click()
  await expect(page.getByText('Stretch & pull').last()).toBeVisible()
  await page.getByRole('button', { name: 'Calendar' }).click()
  await expect(page.getByText('A dot marks your baseline week', { exact: false })).toBeVisible()

  // Export produces a Firewatch backup file.
  await page.getByRole('button', { name: 'Settings' }).click()
  // Hide next piece timer: on, then the Log tab hides the time until a tap.
  await page.getByText('Hide next piece timer', { exact: false }).click()
  await page.getByRole('button', { name: 'Log' }).click()
  await page.getByText('Tap to see your next piece time').click()
  await expect(page.getByText('Tap to see your next piece time')).toHaveCount(0)
  // A craving shows one quiet line, no buttons.
  await page.getByRole('button', { name: 'Craving? Log it' }).click()
  await page.locator('.level').nth(4).click()
  await expect(page.locator('.craving-line')).toBeVisible()
  await expect(page.getByRole('button', { name: 'It passed' })).toHaveCount(0)
  await page.screenshot({ path: 'test-results/craving-line.png', fullPage: true })
  await page.getByRole('button', { name: 'Settings' }).click()
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Export' }).click()])
  const text = await (await download.createReadStream()).toArray().then((c) => Buffer.concat(c).toString())
  const file = JSON.parse(text)
  expect(file.app).toBe('Firewatch by Baastik Labs')
  expect(file.records.filter((r: any) => r.type === 'dose').length).toBe(36)
})

// A brand-new user on the baseline path gets guidance straight away: an early estimate of 8 a day.
test('early estimate for new users', async ({ page }) => {
  await page.goto('./')
  await page.getByRole('button', { name: 'Skip' }).click()
  for (let i = 0; i < 3; i++) await page.getByRole('button', { name: 'Next' }).click()
  await page.getByRole('button', { name: 'Start' }).click()
  await page.locator('.product', { hasText: 'Nicotine gum 4 mg' }).click()
  await expect(page.getByText(/Early estimate/)).toBeVisible()
  await expect(page.locator('.tier')).toHaveText('Blaze')
  await page.screenshot({ path: 'test-results/early.png', fullPage: true })
})
