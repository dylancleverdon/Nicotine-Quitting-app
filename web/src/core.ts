// Thin typed wrapper around the shared Kotlin engine (core module, compiled to JS).
import { FirewatchCore } from 'firewatch-core'

const core = FirewatchCore.getInstance()

export interface Rung { pieces: number; tier: string; label: string; plain: string }
export interface Battery { charge: number; state: 'CLEAR' | 'CHARGING' | 'FULL_AT_WAKE' | 'MORNING_DELAY' | 'WIND_DOWN' | 'ASLEEP'; readyAt: number | null; stretchMin: number; pullMin: number; fitsNow?: string | null; closeToBed?: boolean }
export interface StretchDay { date: string; stretchMin: number; pullMin: number; paused?: boolean }
export interface Relapse { on: boolean; nextAt: number | null; gapMin: number; productId: string | null; productName: string | null; recommend: string | null; movingOn: boolean; modeDays: string[] }
export interface CravingWindow { from: number; to: number; peakAt: number; likelihood: number; strength: number }
export interface Outlooks {
  forecast: { points: number[][]; learning: boolean; cravingsUsed: number; next: CravingWindow | null; windows: CravingWindow[]; quietestAt: number | null; hits: number; tested: number }
  receptors: { history: NamedValue[]; plan: NamedValue[]; stay: NamedValue[]; todayLoad: number; clearAirOnPlan: string | null; typicalOnPlan: string | null; typicalIfStay: string | null; typical: number } | null
}
export interface HelpPage { title: string; body: string }
export interface HelpArticle { id: string; section: string; title: string; body: string }
export interface DoseView { id: string; at: number; name: string; pieces: number; mg: number; estimated: boolean; tags: string[]; kind: string }
export interface CravingView { id: string; at: number; intensity: number; name: string; outcome: string; result: string; endedAt: number | null; tags: string[] }
export interface Review { date: string; pieces: number; netMin: number | null; volatility: number; mix: NamedValue[]; longestGapMin: number | null; stacked: number; morningStretchMin: number | null; tips: string[] }
export interface DayView {
  date: string; pieces: number; mg: number; doses: number; cravings: number; rodeOut: number; level: number
  clearHours: number; quality: number | null; estimated: boolean; hasRange: boolean; low: number; high: number
  awakeHours: number; mouthMin: number; wakeToFirstMin: number | null; spikeMg: number; labelMg: number
  borrowedPieces: number; barcode: boolean[]; doubleUps: number; volatility: number; kinds: Record<string, number>
}
export interface Practice {
  active: Rung | null; untilBedtime: boolean; netMin: number | null; day: number; allowed: Rung[]
  lighterOffer: Rung | null; lighterTitle: string | null; lighterBody: string | null
  workFrom: Rung | null; workFromHours: number; workFromNet: number; lastSessionId: string | null; followUpId: string | null
  explainer: string; stopNote: string; relapseNote: string
}
export interface DayDetail { doses: DoseView[]; cravings: CravingView[]; wave: number[][]; volatility: number[][]; wakeAt: number; sleepAt: number; sleeps: NamedValue[]; levels: string[]; state: string; pieces: number; mg: number; review: Review | null }
export interface NamedValue { label: string; value: number; extra: string }
export interface Recap { key: string; title: string; pieces: number; drop: number | null; longestGapMin: number; trigger: string | null; rungs: string[]; cravings: number }
export interface InsightsData {
  avoidedPieces: number; avoidedMg: number; money: number; winRate: number | null; cravingMinutes: number | null
  heaviness: number | null; taperPct: number | null; taperText: string | null; journey: number | null; arrivals: NamedValue[]
  longestGapMin: number; lightestDay: string | null; lightestPieces: number | null; stretchMin: number; daysAtRung: number
  badges: NamedValue[]; cards: string[]; pouches: number; pouchMetres: number; chewHours: number; cigarettes: number
  clearAirLast: number | null; clearAirSteps: NamedValue[]; heatmap: number[][]; triggers: NamedValue[]; beaten: NamedValue[]
  comparisons: NamedValue[]; weeklyGaps: number[]; staircase: NamedValue[]; overnight: number[]; background: number[]
  recaps: Recap[]; capacity: number; coachConfident: boolean; coachLevels: NamedValue[]; honest: string | null; checkIns: NamedValue[]
}
export interface Snapshot {
  today: string; baselineState: 'none' | 'progress' | 'complete'; baselineDay: number; baselineAverage: number | null
  revealed: boolean; measured: Rung | null; target: Rung | null; battery: Battery | null
  stepDown: Rung | null; stepDownNote: string | null; stepUp: Rung | null; stepUpWhy: string | null; stepUpSameDay: boolean; cravingEndings: NamedValue[]; checks: NamedValue[]; checksToday: number; early: boolean; earlyUpdate: number | null; heldDays: number; held: string[][]; lighterThanStart: number | null; daysOffSmokeAndVape: number | null; welcomeBack: string[] | null; previews: Record<string, number>; steadyDays: number; wakingToday: string; steadyMilestone: number | null; practicing: boolean; practiceFollowUp: Rung | null; taperSteps: NamedValue[]; taperBasis: string | null; headsUps: string[]
  morningStretch: number | null; tip: NamedValue | null; yesterday: Review | null; favourites: NamedValue[]; steadyExplainer: string; netExplainer: string
  practice: Practice; dayStates: Record<string, string>; levelMarks: Record<string, string>; history: NamedValue[]; recentStates: NamedValue[]; known7: number; heldTotal: number
  stepProgress: { held: number; needed: number; next: Rung; offered: boolean; unlocked: boolean; fraction: number; heldHours: number; neededHours: number; todayOver: boolean; unlocksAt: number | null; restartedOn: string | null } | null; doubleUpsWeekly: NamedValue[]; thenCurve: number[]; nowCurve: number[]; unknownNote: string; holdShortNote: string
  clearAir: { active: boolean; daysFree: number; healing: number | null; offer: boolean }
  charts: {
    gapSizes: NamedValue[]; weekShape: NamedValue[]; dailyPeaks: NamedValue[]; firstPiece: string[][]; longestGaps: NamedValue[]; cravingWeekly: NamedValue[]
    netSplit: string[][]; kindsByHour: Record<string, number[]>; steadyByMonth: NamedValue[]; paceVsPlan: string[][]; practiceRuns: NamedValue[]; daysFree: NamedValue[]
  }
  qualityScore: number | null; qualityLabel: string | null; swapTip: string | null; activeCraving: CravingView | null
  todayPieces: number; todayMg: number; todayCravings: number; todayRodeOut: number; lastDoseAt: number | null
  todayDoses: DoseView[]; todayCravingList: CravingView[]; wave: number[][]; wakeAt: number; sleepAt: number; typical: number[]
  days: DayView[]; sevenDayAverage: number[]; insights: InsightsData; ladder: Rung[]; tiers: NamedValue[]
  stretchDays: StretchDay[]; stretchSummary: string | null
  relapse: Relapse; nowMg: number
}

export const Core = {
  outlooks: (records: string): Outlooks => JSON.parse(core.outlooks(records, Date.now())),
  feedbackUrl: (): string => core.feedbackUrl(),
  feedbackBody: (type: string, s: string, d: string, n: string, info: string): string => core.feedbackBody(type, s, d, n, info),
  feedbackPrivacy: (): string => core.feedbackPrivacy(),
  help: (): { tour: HelpPage[]; why: HelpPage[]; articles: HelpArticle[] } => JSON.parse(core.help()),
  travelPrompt: (records: string): NamedValue | null => JSON.parse(core.travelPrompt(records, Intl.DateTimeFormat().resolvedOptions().timeZone, Date.now())),
  travelAnswer: (settings: any, answer: 'first' | 'local' | 'keep'): any => JSON.parse(core.travelAnswer(JSON.stringify(settings), Intl.DateTimeFormat().resolvedOptions().timeZone, Date.now(), answer)),
  themes: (): { id: string; name: string; feel: string }[] => JSON.parse(core.themes()),
  palette: (id: string, mode: string, systemDark: boolean, trueBlack: boolean, calm: boolean, colourBlind: boolean): any =>
    JSON.parse(core.palette(id, mode, systemDark, trueBlack, calm, colourBlind)),
  defaultProducts: (): any[] => JSON.parse(core.defaultProducts()),
  cravingScale: (): NamedValue[] => JSON.parse(core.cravingScale()),
  newId: (): string => core.newId(Date.now()),
  compute: (records: string): Snapshot => JSON.parse(core.compute(records, Date.now(), lastActivity())),
  waitedForFull: (records: string, doseId: string): boolean => core.waitedForFull(records, doseId),
  favouriteCharts: (): string => core.favouriteCharts(),
  day: (records: string, iso: string): DayDetail => JSON.parse(core.day(records, iso, Date.now())),
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
