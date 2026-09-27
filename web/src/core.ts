// Thin typed wrapper around the shared Kotlin engine (core module, compiled to JS).
import { FirewatchCore } from 'firewatch-core'

const core = FirewatchCore.getInstance()

export interface Rung { pieces: number; tier: string; label: string; plain: string }
export interface Battery { charge: number; state: 'CLEAR' | 'CHARGING' | 'FULL_AT_WAKE' | 'MORNING_DELAY' | 'WIND_DOWN' | 'ASLEEP'; readyAt: number | null; stretchMin: number; pullMin: number }
export interface StretchDay { date: string; stretchMin: number; pullMin: number }
export interface DoseView { id: string; at: number; name: string; pieces: number; mg: number; estimated: boolean; tags: string[]; kind: string }
export interface CravingView { id: string; at: number; intensity: number; name: string; outcome: string; endedAt: number | null; tags: string[] }
export interface DayView {
  date: string; pieces: number; mg: number; doses: number; cravings: number; rodeOut: number; level: number
  clearHours: number; quality: number | null; estimated: boolean; hasRange: boolean; low: number; high: number
  awakeHours: number; mouthMin: number; wakeToFirstMin: number | null; spikeMg: number; labelMg: number
  borrowedPieces: number; barcode: boolean[]; doubleUps: number
}
export interface NamedValue { label: string; value: number; extra: string }
export interface Recap { key: string; title: string; pieces: number; drop: number | null; longestGapMin: number; trigger: string | null; rungs: string[]; cravings: number }
export interface InsightsData {
  avoidedPieces: number; avoidedMg: number; money: number; winRate: number | null; cravingMinutes: number | null
  heaviness: number | null; taperPct: number | null; journey: number | null; arrivals: NamedValue[]
  longestGapMin: number; lightestDay: string | null; lightestPieces: number | null; stretchMin: number; daysAtRung: number
  badges: NamedValue[]; cards: string[]; pouches: number; pouchMetres: number; chewHours: number; cigarettes: number
  clearAirLast: number | null; clearAirSteps: NamedValue[]; heatmap: number[][]; triggers: NamedValue[]; beaten: NamedValue[]
  comparisons: NamedValue[]; weeklyGaps: number[]; staircase: NamedValue[]; overnight: number[]; background: number[]
  recaps: Recap[]; capacity: number; coachConfident: boolean; coachLevels: NamedValue[]; honest: string | null; checkIns: NamedValue[]
}
export interface Snapshot {
  today: string; baselineState: 'none' | 'progress' | 'complete'; baselineDay: number; baselineAverage: number | null
  revealed: boolean; measured: Rung | null; target: Rung | null; battery: Battery | null
  stepDown: Rung | null; stepDownNote: string | null; stepUp: Rung | null; headsUps: string[]
  qualityScore: number | null; qualityLabel: string | null; swapTip: string | null; activeCraving: CravingView | null
  todayPieces: number; todayMg: number; todayCravings: number; todayRodeOut: number; lastDoseAt: number | null
  todayDoses: DoseView[]; todayCravingList: CravingView[]; wave: number[][]; wakeAt: number; sleepAt: number; typical: number[]
  days: DayView[]; sevenDayAverage: number[]; insights: InsightsData; ladder: Rung[]; tiers: NamedValue[]
  stretchDays: StretchDay[]; stretchSummary: string | null
}

export const Core = {
  defaultProducts: (): any[] => JSON.parse(core.defaultProducts()),
  cravingScale: (): NamedValue[] => JSON.parse(core.cravingScale()),
  newId: (): string => core.newId(Date.now()),
  compute: (records: string): Snapshot => JSON.parse(core.compute(records, Date.now(), lastActivity())),
  waitedForFull: (records: string, doseId: string): boolean => core.waitedForFull(records, doseId),
  day: (records: string, iso: string): { doses: DoseView[]; cravings: CravingView[] } => JSON.parse(core.day(records, iso, Date.now())),
  piecesOf: (product: any, records: string): number => core.absorbedPieces(JSON.stringify(product), records),
  doseFor: (product: any, at: number, opts: { multiplier?: number; duration?: string; acidic?: boolean; tags?: string[] } = {}): any =>
    JSON.parse(core.doseFor(JSON.stringify(product), core.newId(Date.now()), at, Date.now(), opts.multiplier ?? 1, opts.duration ?? 'FULL', opts.acidic ?? false, (opts.tags ?? []).join(','))),
  vapeDose: (at: number, strength: number | null, low: number, high: number, name: string): any =>
    JSON.parse(core.vapeDose(core.newId(Date.now()), at, Date.now(), strength ?? NaN, low, high, name)),
  backfillDays: (todayIso: string): string[] => JSON.parse(core.backfillDays(todayIso)),
  backfillDay: (records: string, iso: string, counts: Record<string, number>, vapes: Record<string, number>): any[] =>
    JSON.parse(core.backfillDay(records, iso, JSON.stringify(counts), JSON.stringify(vapes), Date.now())),
}

/** When the app was last opened: after bedtime this tells the battery "I'm up". */
export const lastActivity = (): number => Number(localStorage.getItem('fw_last_activity') ?? 0)
export const markActivity = () => localStorage.setItem('fw_last_activity', String(Date.now()))
