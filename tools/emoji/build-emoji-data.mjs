// Regenerates public/emoji-data.json from @emoji-mart/data.
//
// Run with `just update-emoji-data` (or `node tools/emoji/build-emoji-data.mjs`) whenever you want to pick up a
// newer Unicode / emoji-mart release. This is NOT part of any build — the generated file is committed.
//
// No npm dependency on purpose: Node 18+ has global fetch, and the picker itself is pure Scala.
//
// Upstream shape (sets/15/native.json):
//   { categories: [{ id, emojis: [id] }], emojis: { id: { id, name, emoticons, keywords, skins: [{unified, native}],
//     version } }, aliases: {...}, sheet: {...} }
//
// Trimmed shape (what com.togger.ui.emoji.EmojiDataLoader parses) — see claude_files/emoji_picker.md §1.3:
//   { source, categories: [{ id, emojis: [id] }], emojis: { id: { name, keywords, skins: [native] } } }
//
// Dropped: aliases, emoticons, unified codepoints, per-emoji version, the sheet block.

import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const VERSION = '1.2.1'
const SOURCE = `@emoji-mart/data@${VERSION} sets/15/native.json`
const URL = `https://cdn.jsdelivr.net/npm/@emoji-mart/data@${VERSION}/sets/15/native.json`

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
const outPath = resolve(repoRoot, 'public', 'emoji-data.json')

const response = await fetch(URL)
if (!response.ok) {
  throw new Error(`Failed to fetch ${URL}: ${response.status} ${response.statusText}`)
}
const upstream = await response.json()

const categories = upstream.categories.map((category) => ({
  id: category.id,
  emojis: category.emojis.filter((id) => upstream.emojis[id] != null),
}))

const emojis = {}
for (const [id, emoji] of Object.entries(upstream.emojis)) {
  const skins = emoji.skins.map((skin) => skin.native).filter(Boolean)
  if (skins.length === 0) continue
  emojis[id] = {
    name: emoji.name,
    keywords: emoji.keywords ?? [],
    skins,
  }
}

const trimmed = { source: SOURCE, categories, emojis }
const json = JSON.stringify(trimmed)

mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(outPath, json + '\n', 'utf8')

const toned = Object.values(emojis).filter((emoji) => emoji.skins.length > 1).length
console.log(`Wrote ${outPath}`)
console.log(`  source     ${SOURCE}`)
console.log(`  categories ${categories.length}`)
console.log(`  emojis     ${Object.keys(emojis).length} (${toned} with skin tones)`)
console.log(`  size       ${(json.length / 1024).toFixed(0)} KB raw`)
