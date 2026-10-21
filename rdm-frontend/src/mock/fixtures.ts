import type { Category, Fundable, Suggestion, User } from '../types'

export const mockUser: User = {
  id: '208412739182465024',
  displayName: 'Mara Quill',
  username: 'maraquill',
  avatarUrl: null,
  roles: ['voter'],
}

export const mockCategories: Category[] = [
  {
    id: 'goty',
    order: 1,
    name: 'Game of the Year',
    description: 'The single finest game of the season, judged across every discipline.',
    criteria: 'Any title released between 1 January and 30 November. Re-releases are not eligible.',
    maxSuggestions: 3,
  },
  {
    id: 'direction',
    order: 2,
    name: 'Outstanding Direction',
    description: 'For the creative vision that held an entire production together.',
    criteria: 'Name the game; the jury identifies the credited director.',
  },
  {
    id: 'narrative',
    order: 3,
    name: 'Achievement in Narrative',
    description: 'Writing, structure and character work that lingered after the credits.',
  },
  {
    id: 'art-direction',
    order: 4,
    name: 'Distinguished Art Direction',
    description: 'Visual identity, world-building and the discipline behind a coherent look.',
  },
  {
    id: 'score',
    order: 5,
    name: 'Original Score and Sound',
    description: 'Composition, sound design and the craft of making a world audible.',
  },
  {
    id: 'performance',
    order: 6,
    name: 'Best Performance',
    description: 'A single leading or supporting performance in a released title.',
    criteria: 'Name the performer and the role, e.g. "A. Nolan as Vesper".',
  },
  {
    id: 'independent',
    order: 7,
    name: 'Independent Game of the Year',
    description: 'The finest work from a studio operating without a publisher.',
  },
  {
    id: 'ongoing',
    order: 8,
    name: 'Best Ongoing Game',
    description: 'A live title that grew meaningfully over the past twelve months.',
  },
  {
    id: 'debut',
    order: 9,
    name: 'Debut Studio of the Year',
    description: 'A first commercial release that announced a studio worth watching.',
    locked: true,
  },
  {
    id: 'community',
    order: 10,
    name: 'Community Choice',
    description: 'The people\u2019s pick, tallied from the open floor.',
    maxSuggestions: 1,
  },
]

export const mockSuggestions: Suggestion[] = [
  { id: 'sg_1', categoryId: 'goty', text: 'Hollow Fields', updatedAt: '2026-09-02T18:11:00Z' },
  { id: 'sg_2', categoryId: 'goty', text: 'Sable Harbour', updatedAt: '2026-09-02T18:11:00Z' },
  { id: 'sg_3', categoryId: 'narrative', text: 'The Cartographer\u2019s Widow', updatedAt: '2026-09-04T09:40:00Z' },
  { id: 'sg_4', categoryId: 'debut', text: 'Nine Tenths Studio', updatedAt: '2026-08-21T12:02:00Z' },
]

export const mockFundables: Fundable[] = [
  {
    id: 1,
    publicId: 'FP-0001',
    name: 'community-library',
    title: 'A neighbourhood library of things',
    description: 'Help establish a shared collection of tools, games and equipment for the community.',
    origin: { type: 'council_resolution', id: '2026-17', title: 'Council Resolution 2026-17', url: null },
    createdBy: mockUser.id,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-12T14:30:00Z',
    editHistory: [{ id: 'rev-1', editedAt: '2026-09-12T14:30:00Z', editedBy: mockUser.id, summary: 'Updated the hosting estimate.' }],
    fullyFunded: false,
    components: [
      {
        id: 101, publicId: 'FP-0001-C01', fundableId: 1, name: 'Opening inventory', description: 'The initial collection and storage.',
        useOfFunds: 'Shelving, labels, durable storage, and the initial shared inventory.',
        createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z', editHistory: [],
        amountCent: 240000, paymentSchedule: { type: 'one_time' }, interestedCount: 18,
        committedCount: 6, totalCommittedAmount: 128000, fullyFunded: false, amountCommitted: 128000,
        lifetimeCost: 240000, equalDivisionOnRemaining: 6222, projectedOverFundReturnPP: null,
      },
      {
        id: 102, publicId: 'FP-0001-C02', fundableId: 1, name: 'Monthly hosting', description: 'Rent and maintenance for the first year.',
        useOfFunds: 'Venue hire, utilities, insurance, and routine maintenance for twelve months.',
        createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-12T14:30:00Z', editHistory: [],
        amountCent: 120000, paymentSchedule: { type: 'recurring', intervalMonths: 1, occurrences: 12 },
        interestedCount: 12, committedCount: 4, totalCommittedAmount: 56000, fullyFunded: false,
        amountCommitted: 56000, lifetimeCost: 120000, equalDivisionOnRemaining: 5333, projectedOverFundReturnPP: null,
      },
    ],
  },
  {
    id: 2,
    publicId: 'FP-0002',
    name: 'solar-workshop',
    title: 'Solar workshop equipment',
    description: 'Fund a practical workshop that teaches renewable energy skills.',
    origin: { type: 'council_resolution', id: '2026-21', title: 'Council Resolution 2026-21', url: null },
    createdBy: mockUser.id,
    createdAt: '2026-09-05T09:00:00Z',
    updatedAt: '2026-09-05T09:00:00Z',
    editHistory: [],
    fullyFunded: true,
    components: [{
      id: 201, publicId: 'FP-0002-C01', fundableId: 2, name: 'Workshop kit', description: 'Panels, batteries and safety equipment.',
      useOfFunds: 'Solar panels, batteries, wiring, tools, and participant safety equipment.',
      createdAt: '2026-09-05T09:00:00Z', updatedAt: '2026-09-05T09:00:00Z', editHistory: [],
      amountCent: 850000, paymentSchedule: { type: 'one_time' }, interestedCount: 31,
      committedCount: 24, totalCommittedAmount: 850000, fullyFunded: true, amountCommitted: 850000,
      lifetimeCost: 850000, equalDivisionOnRemaining: 0, projectedOverFundReturnPP: 0,
    }],
  },
]
