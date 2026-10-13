import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { PantrySchema, type Item, type Pantry, type Rules, type Settings } from "./types.js";
import { slugify } from "./text.js";

export class PantryStore {
  constructor(readonly path: string) {}

  async load(): Promise<Pantry> {
    try {
      const raw = await readFile(this.path, "utf8");
      return PantrySchema.parse(JSON.parse(raw));
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === "ENOENT") {
        return PantrySchema.parse({ version: 1 });
      }
      throw err;
    }
  }

  async save(pantry: Pantry): Promise<void> {
    const validated = PantrySchema.parse(pantry);
    await mkdir(dirname(this.path), { recursive: true });
    const tmp = `${this.path}.tmp-${process.pid}`;
    await writeFile(tmp, JSON.stringify(validated, null, 2) + "\n", "utf8");
    await rename(tmp, this.path);
  }

  /** Load, apply a mutation, validate and save atomically (last writer wins). */
  async update<T>(fn: (pantry: Pantry) => T): Promise<T> {
    const pantry = await this.load();
    const result = fn(pantry);
    await this.save(pantry);
    return result;
  }
}

/* ---------- Pure pantry operations (used by CLI and MCP) ---------- */

export function findItem(pantry: Pantry, id: string): Item {
  const item = pantry.items.find((i) => i.id === id);
  if (!item) {
    const known = pantry.items.map((i) => i.id).join(", ") || "(none)";
    throw new Error(`No pantry item "${id}". Known items: ${known}`);
  }
  return item;
}

export interface NewItem {
  id?: string;
  name: string;
  unit: Item["unit"];
  expected: number;
  actual?: number;
  query?: string;
  rules?: Partial<Rules>;
  notes?: string;
}

export function addItem(pantry: Pantry, input: NewItem): Item {
  const id = input.id ?? slugify(input.name);
  if (pantry.items.some((i) => i.id === id)) {
    throw new Error(`Pantry item "${id}" already exists`);
  }
  const item: Item = {
    id,
    name: input.name,
    unit: input.unit,
    expected: input.expected,
    actual: input.actual ?? 0,
    paused: false,
    rules: {
      allow: [],
      deny: [],
      allowPrivateLabel: true,
      ...input.rules,
    },
    ...(input.query ? { query: input.query } : {}),
    ...(input.notes ? { notes: input.notes } : {}),
  };
  pantry.items.push(item);
  return item;
}

export function removeItem(pantry: Pantry, id: string): void {
  findItem(pantry, id);
  pantry.items = pantry.items.filter((i) => i.id !== id);
}

export function setActuals(pantry: Pantry, actuals: Record<string, number>): Item[] {
  const changed: Item[] = [];
  for (const [id, qty] of Object.entries(actuals)) {
    if (!Number.isFinite(qty) || qty < 0) throw new Error(`Invalid quantity for "${id}": ${qty}`);
    const item = findItem(pantry, id);
    item.actual = qty;
    changed.push(item);
  }
  return changed;
}

export function addToActuals(pantry: Pantry, additions: Record<string, number>): Item[] {
  const changed: Item[] = [];
  for (const [id, qty] of Object.entries(additions)) {
    if (!Number.isFinite(qty) || qty < 0) throw new Error(`Invalid quantity for "${id}": ${qty}`);
    const item = findItem(pantry, id);
    item.actual = Math.round((item.actual + qty) * 1e6) / 1e6;
    changed.push(item);
  }
  return changed;
}

export interface RuleEdit {
  allowAdd?: string[];
  allowRemove?: string[];
  denyAdd?: string[];
  denyRemove?: string[];
  /** null clears the cap. */
  maxUnitPrice?: number | null;
  maxPackPrice?: number | null;
  minPackSize?: number | null;
  maxPackSize?: number | null;
  allowPrivateLabel?: boolean;
  clearAllow?: boolean;
  clearDeny?: boolean;
}

export function editRules(pantry: Pantry, id: string, edit: RuleEdit): Item {
  const item = findItem(pantry, id);
  const r = item.rules;
  if (edit.clearAllow) r.allow = [];
  if (edit.clearDeny) r.deny = [];
  const lower = (s: string) => s.trim().toLowerCase();
  if (edit.allowRemove) r.allow = r.allow.filter((a) => !edit.allowRemove!.some((x) => lower(x) === lower(a)));
  if (edit.denyRemove) r.deny = r.deny.filter((a) => !edit.denyRemove!.some((x) => lower(x) === lower(a)));
  for (const a of edit.allowAdd ?? []) if (!r.allow.some((x) => lower(x) === lower(a))) r.allow.push(a.trim());
  for (const d of edit.denyAdd ?? []) if (!r.deny.some((x) => lower(x) === lower(d))) r.deny.push(d.trim());
  const apply = <K extends "maxUnitPrice" | "maxPackPrice" | "minPackSize" | "maxPackSize">(key: K) => {
    const v = edit[key];
    if (v === undefined) return;
    if (v === null) delete r[key];
    else r[key] = v;
  };
  apply("maxUnitPrice");
  apply("maxPackPrice");
  apply("minPackSize");
  apply("maxPackSize");
  if (edit.allowPrivateLabel !== undefined) r.allowPrivateLabel = edit.allowPrivateLabel;
  return item;
}

export function updateSettings(pantry: Pantry, patch: Partial<Settings>): Settings {
  pantry.settings = { ...pantry.settings, ...patch };
  return pantry.settings;
}

export function needOf(item: Item): number {
  return Math.max(0, Math.round((item.expected - item.actual) * 1e6) / 1e6);
}
