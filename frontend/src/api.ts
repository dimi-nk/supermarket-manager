// Typed client for the backend REST API. Types mirror the Java records.

export type Measure = "kg" | "l" | "each";
export type MixPolicy = "SINGLE_PRODUCT" | "SINGLE_BRAND" | "ANY";

export interface Category { id: string; name: string; parentId: string | null; measure: Measure; searchQuery: string | null; notes: string | null }
export interface CatalogProduct {
  id: string; name: string; brand: string | null; categoryId: string; packSize: number; barcode: string | null;
  externalSource: string | null; externalId: string | null; searchQuery: string | null; privateLabel: boolean; notes: string | null;
}
export interface Profile { id: string; name: string; description: string | null; active: boolean; createdAt: string }
export interface Target { subject: string; amount: number; packCount?: number | null; packSize?: number | null; note?: string | null }
export interface TargetVersion { id: number; profileId: string; effectiveFrom: string; note: string | null; createdAt: string; targets: Target[] }
export interface StockEntry { subject: string; name: string; measure: Measure; amount: number; updatedAt: string | null }
export interface StockEvent { id: number; at: string; kind: string; subject: string; delta: number; resulting: number; price?: number; retailer?: string; reference?: string; note?: string }
export interface DeltaLine { subject: string; name: string; measure: Measure; target: number; have: number; fromTargetsBelow: number; need: number; packSize?: number | null; suggestedPacks?: number | null }
export interface DeltaReport { profileId: string; versionId: number; effectiveFrom: string; at: string; lines: DeltaLine[]; basket: Basket }

export interface ProductRef { id?: string | null; source?: string | null; name?: string | null; brand?: string | null; barcode?: string | null; packSize?: number | null; privateLabel?: boolean | null }
export interface PinnedOffer { retailer?: string | null; retailerName?: string | null; price?: number | null; packSize?: number | null }
export interface BasketLine {
  id: string; label?: string; query?: string | null; measure: Measure; quantity: number; categoryId?: string | null;
  categoryPath?: string[]; product?: ProductRef | null; preferredPackSize?: number | null; offer?: PinnedOffer | null; note?: string | null;
}
export interface Basket { name?: string; lines: BasketLine[]; meta?: Record<string, string> }

export interface RetailerOffer { retailer: string; retailerName: string; price: number; reportedUnitPrice?: number; discount: boolean; lastUpdated?: string; url?: string }
export interface Product { source: string; id: string; name: string; brand?: string; barcode?: string; unit?: string; unitQuantity?: number; privateLabel: boolean; offers: RetailerOffer[] }
export interface Candidate {
  source: string; productId: string; name: string; brand?: string; barcode?: string; privateLabel: boolean; measure: Measure; packSize: number;
  retailer: string; retailerName: string; price?: number; discount: boolean; lastUpdated?: string; url?: string; unitPrice?: number; unitPriceMismatch: boolean;
}
export interface Diagnostics { searched: number; rejected: Record<string, number>; closestRejected?: { name: string; retailerName: string; unitPrice: number; reason: string }; notes: string[] }
export interface LineCandidates { line: BasketLine; candidates: Candidate[]; diagnostics: Diagnostics; mixPolicy: MixPolicy }
export interface CandidateSet { source: string; lines: LineCandidates[]; warnings: string[] }
export interface FillPart { candidate: Candidate; packs: number; cost?: number }
export interface ResolvedLine { lineId: string; label: string; measure: Measure; need: number; amount: number; cost?: number; parts: FillPart[] }
export interface StoreBasket { retailer: string; retailerName: string; subtotal: number; fee: number; total: number; belowMinimum: boolean; lines: ResolvedLine[] }
export interface UnresolvedLine { lineId: string; label: string; status: "UNFULFILLED" | "LEFT_OUT"; reason: string }
export interface ResolvedBasket {
  resolver: string; generatedAt: string; total?: number; stores: StoreBasket[]; unresolved: UnresolvedLine[];
  alternatives: { maxRetailers: number; total: number; retailers: string[] }[]; warnings: string[];
}

export type RuleType = "DENY" | "ALLOW" | "NO_PRIVATE_LABEL" | "UNIT_PRICE" | "PACK_PRICE" | "PACK_SIZE" | "RETAILER_DENY" | "RETAILER_ALLOW" | "ONLY_DISCOUNTED" | "MIX_POLICY";
export interface Rule { id?: string; type: RuleType; scope?: { lineId?: string | null; categoryId?: string | null }; values?: string[]; min?: number | null; max?: number | null; mix?: MixPolicy | null; enabled?: boolean; note?: string | null }
export interface RuleSet { id?: string; name: string; description?: string | null; rules: Rule[]; updatedAt?: string }

export interface CartHandoff { retailer: string; retailerName: string; total: number; items: { lineId: string; product: string; brand?: string; packs: number; price?: number; cost?: number; url?: string }[] }
export interface Stage { name: string; detail: string; millis: number }
export interface PipelineResult {
  runId?: string; createdAt: string; stages: Stage[]; delta?: DeltaReport; basket?: Basket; candidates?: CandidateSet;
  resolved?: ResolvedBasket; checklist?: string; handoffs?: CartHandoff[]; appliedAt?: string;
}
export interface PipelineRequest {
  source: { type: "pantry" | "basket" | "candidates"; profileId?: string; at?: string; basket?: Basket; candidateSet?: CandidateSet };
  candidates?: string | null;
  rules?: { ruleSetId?: string; ruleSet?: RuleSet } | null;
  resolve?: { resolver: string; maxRetailers?: number; retailer?: string; packRoundingTolerance?: number } | null;
  export?: boolean;
  save?: boolean;
}
export interface RunSummary { id: string; createdAt: string; summary: string; total?: number; appliedAt?: string }
export interface StageCatalog { candidateSources: { id: string; description: string }[]; resolvers: { id: string; description: string }[] }
export interface ApplyResult { applied: { subject: string; amount: number }[]; skipped: string[] }
export interface PriceSettings { maxRetailers: number; packRoundingTolerance: number; maxPriceAgeDays: number; searchPages: number }
export interface RetailerSetting { retailer: string; displayName?: string | null; fee: number; freeAbove?: number | null; minBasket?: number | null }
export interface SystemInfo { app: string; version: string; dataDir: string; database: string; mcpUrl: string; java: string }

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: body === undefined ? {} : { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const problem = await res.json();
      message = problem.detail || problem.message || message;
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get("content-type") ?? "";
  return (type.includes("json") ? res.json() : res.text()) as Promise<T>;
}

const enc = encodeURIComponent;
const get = <T,>(p: string) => request<T>("GET", p);
const post = <T,>(p: string, b?: unknown) => request<T>("POST", p, b ?? {});
const put = <T,>(p: string, b: unknown) => request<T>("PUT", p, b);
const del = (p: string) => request<void>("DELETE", p);

export const api = {
  system: { info: () => get<SystemInfo>("/api/system/info"), shutdown: () => post<{ message: string }>("/api/system/shutdown") },
  pantry: {
    categories: () => get<Category[]>("/api/pantry/categories"),
    saveCategory: (c: Partial<Category>) => post<Category>("/api/pantry/categories", c),
    deleteCategory: (id: string) => del(`/api/pantry/categories/${enc(id)}`),
    products: () => get<CatalogProduct[]>("/api/pantry/products"),
    saveProduct: (p: Partial<CatalogProduct>) => post<CatalogProduct>("/api/pantry/products", p),
    deleteProduct: (id: string) => del(`/api/pantry/products/${enc(id)}`),
    profiles: () => get<Profile[]>("/api/pantry/profiles"),
    saveProfile: (p: Partial<Profile>) => post<Profile>("/api/pantry/profiles", p),
    activate: (id: string) => post<Profile>(`/api/pantry/profiles/${enc(id)}/activate`),
    deleteProfile: (id: string) => del(`/api/pantry/profiles/${enc(id)}`),
    versions: (id: string) => get<TargetVersion[]>(`/api/pantry/profiles/${enc(id)}/versions`),
    createVersion: (id: string, v: { effectiveFrom?: string; note?: string; targets?: Target[] | null }) =>
      post<TargetVersion>(`/api/pantry/profiles/${enc(id)}/versions`, v),
    updateVersion: (vid: number, v: { effectiveFrom?: string; note?: string; targets?: Target[] }) => put<TargetVersion>(`/api/pantry/versions/${vid}`, v),
    deleteVersion: (vid: number) => del(`/api/pantry/versions/${vid}`),
    delta: (profileId?: string, at?: string) => {
      const q = new URLSearchParams();
      if (profileId) q.set("profileId", profileId);
      if (at) q.set("at", at);
      return get<DeltaReport>(`/api/pantry/delta?${q}`);
    },
    stock: () => get<StockEntry[]>("/api/pantry/stock"),
    setStock: (subject: string, amount: number, note?: string) => put<StockEntry>(`/api/pantry/stock/${enc(subject)}`, { amount, note }),
    addStock: (subject: string, delta: number, note?: string) => post<StockEntry>(`/api/pantry/stock/${enc(subject)}/add`, { delta, note }),
    removeStock: (subject: string) => del(`/api/pantry/stock/${enc(subject)}`),
    history: (subject?: string) => get<StockEvent[]>(`/api/pantry/stock/history${subject ? `?subject=${enc(subject)}` : ""}`),
    loadExamples: () => post<{ message: string }>("/api/pantry/examples"),
  },
  rules: {
    sets: () => get<RuleSet[]>("/api/rules/sets"),
    save: (s: RuleSet) => post<RuleSet>("/api/rules/sets", s),
    delete: (id: string) => del(`/api/rules/sets/${enc(id)}`),
  },
  prices: {
    providers: () => get<{ id: string; candidateSource: string; description: string }[]>("/api/prices/providers"),
    search: (provider: string, q: string) => get<Product[]>(`/api/prices/search?provider=${enc(provider)}&q=${enc(q)}`),
    settings: () => get<PriceSettings>("/api/prices/settings"),
    saveSettings: (s: PriceSettings) => put<PriceSettings>("/api/prices/settings", s),
    retailers: () => get<RetailerSetting[]>("/api/prices/retailers"),
    saveRetailer: (r: RetailerSetting) => put<RetailerSetting>(`/api/prices/retailers/${enc(r.retailer)}`, r),
    deleteRetailer: (id: string) => del(`/api/prices/retailers/${enc(id)}`),
    clearCache: () => request<{ deleted: number }>("DELETE", "/api/prices/cache"),
  },
  supermarkets: {
    list: () => get<{ id: string; displayName: string; capabilities: string[]; candidateSource: string }[]>("/api/supermarkets"),
    search: (id: string, q: string) => get<Product[]>(`/api/supermarkets/${enc(id)}/search?q=${enc(q)}`),
    exportCsv: (b: ResolvedBasket) => request<string>("POST", "/api/supermarkets/export?format=csv", b),
  },
  pipeline: {
    stages: () => get<StageCatalog>("/api/pipeline/stages"),
    run: (r: PipelineRequest) => post<PipelineResult>("/api/pipeline/run", r),
    runs: () => get<RunSummary[]>("/api/pipeline/runs"),
    get: (id: string) => get<PipelineResult>(`/api/pipeline/runs/${enc(id)}`),
    delete: (id: string) => del(`/api/pipeline/runs/${enc(id)}`),
    apply: (id: string, skip: string[], force = false) => post<ApplyResult>(`/api/pipeline/runs/${enc(id)}/apply`, { skip, force }),
  },
};

export function fmtQty(v: number, m: Measure): string {
  if (m === "each") return `${Math.round(v * 100) / 100} pcs`;
  if (m === "kg") return v < 1 ? `${Math.round(v * 1000)} g` : `${Math.round(v * 1000) / 1000} kg`;
  return v < 1 ? `${Math.round(v * 1000)} ml` : `${Math.round(v * 1000) / 1000} l`;
}

export const eur = (v?: number | null) => (v === undefined || v === null ? "—" : `€${v.toFixed(2)}`);
export const today = () => new Date().toISOString().slice(0, 10);
