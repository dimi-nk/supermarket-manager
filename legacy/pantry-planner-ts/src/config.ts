import { dirname, join, resolve } from "node:path";
import { PantryStore } from "./store.js";
import { CachedProvider } from "./providers/cached.js";
import { FixtureProvider } from "./providers/fixture.js";
import { PosoKaneiProvider } from "./providers/posokanei.js";
import type { PriceProvider } from "./types.js";

export interface Runtime {
  store: PantryStore;
  provider: PriceProvider;
  /** Where the most recent plan is kept so purchases can be applied to stock later. */
  lastPlanPath: string;
}

/**
 * Environment:
 *   PANTRY_FILE      pantry JSON (default ./pantry.json)
 *   PANTRY_PROVIDER  "posokanei" (default) or "fixture"
 *   PANTRY_FIXTURE   fixture JSON when provider=fixture
 *   PANTRY_CACHE_TTL_MIN  price cache lifetime in minutes (default 360, 0 disables)
 */
export function createRuntime(env: NodeJS.ProcessEnv = process.env): Runtime {
  const file = resolve(env.PANTRY_FILE ?? "pantry.json");
  const dir = dirname(file);
  const store = new PantryStore(file);

  const kind = (env.PANTRY_PROVIDER ?? "posokanei").toLowerCase();
  let provider: PriceProvider;
  if (kind === "fixture") {
    if (!env.PANTRY_FIXTURE) throw new Error("PANTRY_PROVIDER=fixture requires PANTRY_FIXTURE=<path>");
    provider = new FixtureProvider(resolve(env.PANTRY_FIXTURE));
  } else if (kind === "posokanei") {
    const ttlMin = Number(env.PANTRY_CACHE_TTL_MIN ?? 360);
    const raw = new PosoKaneiProvider();
    provider = new CachedProvider(raw, join(dir, ".pantry-cache"), (Number.isFinite(ttlMin) ? ttlMin : 360) * 60_000);
  } else {
    throw new Error(`Unknown PANTRY_PROVIDER "${kind}" (use "posokanei" or "fixture")`);
  }
  return { store, provider, lastPlanPath: join(dir, "last-plan.json") };
}
