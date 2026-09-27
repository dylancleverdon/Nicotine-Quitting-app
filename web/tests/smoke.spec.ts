import { expect, test } from '@playwright/test'

// Onboarding with a back-dated week -> starting tier offered -> log a dose -> reload keeps it -> export.
test('back-dated start, logging and persistence', async ({ page }) => {
  await page.goto('./')
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

  // One tap logs a dose.
  await page.locator('.product', { hasText: 'Nicotine gum 4 mg' }).click()
  await expect(page.getByText('Logged Nicotine gum 4 mg')).toBeVisible()
  await expect(page.locator('.list .item')).toHaveCount(1)

  // Survives a reload (IndexedDB).
  await page.reload()
  await expect(page.locator('.list .item')).toHaveCount(1)
  await expect(page.locator('.tier')).toHaveText('Flicker')

  // Insights and calendar render.
  await page.getByRole('button', { name: 'Insights' }).click()
  await expect(page.getByText('Blood-level wave')).toBeVisible()
  await page.getByRole('button', { name: 'Calendar' }).click()
  await expect(page.getByText('A dot marks your baseline week', { exact: false })).toBeVisible()

  // Export produces a Firewatch backup file.
  await page.getByRole('button', { name: 'Settings' }).click()
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Export' }).click()])
  const text = await (await download.createReadStream()).toArray().then((c) => Buffer.concat(c).toString())
  const file = JSON.parse(text)
  expect(file.app).toBe('Firewatch by Baastik Labs')
  expect(file.records.filter((r: any) => r.type === 'dose').length).toBe(36)
})
